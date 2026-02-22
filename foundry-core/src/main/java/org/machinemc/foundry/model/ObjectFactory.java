package org.machinemc.foundry.model;

import org.jetbrains.annotations.ApiStatus;
import org.machinemc.foundry.util.ASMUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

/**
 * Class that is responsible for constructing and deconstructing objects,
 * writing their data into {@link ModelDataContainer}.
 * <p>
 * This class is for internal use only.
 *
 * @param <T> object type
 */
@ApiStatus.Internal
public abstract class ObjectFactory<T> {

    /**
     * Creates new object factory for objects of given type.
     *
     * @param type type of the object
     * @return object factory for objects of given type
     * @param <T> object type
     */
    public static <T> ObjectFactory<T> create(Class<T> type) {
        return create(type, ClassModel.of(type));
    }

    /**
     * Creates new object factory for objects of given type.
     *
     * @param type type of the object
     * @param classModel class model of the type
     * @return object factory for objects of given type
     * @param <T> object type
     */
    public static <T> ObjectFactory<T> create(Class<T> type, ClassModel classModel) {
        //noinspection unchecked
        return (ObjectFactory<T>) load(type, classModel);
    }

    private final ModelDataContainer.Factory holderFactory;

    /**
     * @param classModel class model for the type of this factory
     */
    protected ObjectFactory(ClassModel classModel) {
        holderFactory = ModelDataContainer.Factory.of(classModel);
    }

    /**
     * @return new empty model data container for the type of this factory
     */
    protected ModelDataContainer newContainer() {
        return holderFactory.get();
    }

    /**
     * Creates new model data container and writes the data of given object to it.
     *
     * @param instance object to write the data from
     * @return new model data container with written data from the object
     */
    public abstract ModelDataContainer write(T instance);

    /**
     * Creates new instance of the factory' type and populates it with the
     * data in given model data container.
     *
     * @param container container
     * @return new instance with data read from the container
     */
    public abstract T read(ModelDataContainer container);

    /**
     * Part of object factory that reads or writes some attributes.
     * <p>
     * This class does not handle the instance or data container creation
     * but only individual read or writes.
     */
    public interface ObjectFactoryPart<T> {

        /**
         * Writes some of {@code instance} data to the provided container.
         *
         * @param instance instance to read the data from
         * @param container container to write the data to
         */
        void write(T instance, ModelDataContainer container);

        /**
         * Reads some data of the provided container and writes them to the {@code instance}.
         *
         * @param container container to read the data from
         * @param instance instance to write the data to
         */
        void read(ModelDataContainer container, T instance);

    }

    private static final String WRITE_METHOD_NAME = "write";
    private static final String READ_METHOD_NAME = "read";
    private static final String NEW_CONTAINER_METHOD_NAME = "newContainer";

    /**
     * Generates object factory class implementation for objects of given type and
     * returns its instance.
     * <p>
     * To get the object factory instance use {@link #create(Class)} as this
     * method is not thread safe.
     *
     * @param type type to create the object factory for
     * @return object factory instance
     */
    private static ObjectFactory<?> load(Class<?> type, ClassModel classModel) {
        ClassData.Builder classDataBuilder = ClassData.builder();

        ClassModel.ConstructionMethod constructionMethod = classModel.getConstructionMethod();

        if (constructionMethod instanceof ClassModel.CustomConstructor<?> custom)
            classDataBuilder.reserveConstructor(custom);

        Map<Class<?>, List<ModelAttribute>> attributes = Arrays.stream(classModel.getAttributes())
                .collect(Collectors.groupingBy(
                        ModelAttribute::source,
                        LinkedHashMap::new,
                        Collectors.toList()));

        attributes.forEach((parent, parentAttributes) -> {
            // for records we do not generate the read implementation as all fields are set in the constructor
            boolean includeRead = !(constructionMethod instanceof ClassModel.RecordConstructor);
            var part = createObjectFactoryPart(parent, true, includeRead, parentAttributes);
            classDataBuilder.reserveParentAccessor(parent, part);
        });

        ClassData classData = classDataBuilder.build();

        Type sourceT = Type.getType(type);
        Type thisT = Type.getObjectType(sourceT.getInternalName() + "$ObjectFactory");

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V25, ACC_PUBLIC, thisT.getInternalName(), null,
                Type.getInternalName(ObjectFactory.class), new String[0]);

        // constructor
        visitObjectFactoryConstructor(cw);

        // write
        {
            var writeDescriptor = Type.getMethodDescriptor(Type.getType(ModelDataContainer.class),
                    Type.getType(Object.class));
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, WRITE_METHOD_NAME, writeDescriptor,
                    null, null);
            GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, WRITE_METHOD_NAME, writeDescriptor);

            ga.visitCode();

            ga.loadThis();
            ga.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ObjectFactory.class), NEW_CONTAINER_METHOD_NAME,
                    Type.getMethodDescriptor(Type.getType(ModelDataContainer.class)), false);

            for (Class<?> parent : attributes.keySet()) {
                ga.dup();
                classData.loadOnStack(thisT, ga, classData.parentAccessorIdx(parent));
                ga.swap();
                ga.loadArg(0);
                ga.swap();
                ga.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ObjectFactoryPart.class), WRITE_METHOD_NAME,
                        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class),
                                Type.getType(ModelDataContainer.class)), true);
            }

            ga.returnValue();
            ga.endMethod();
        }

        // read
        if (constructionMethod instanceof ClassModel.RecordConstructor) {
            visitReadForRecord(cw, sourceT, List.of(classModel.getAttributes()));
        } else {
            var readDescriptor = Type.getMethodDescriptor(Type.getType(Object.class),
                    Type.getType(ModelDataContainer.class));
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, READ_METHOD_NAME, readDescriptor,
                    null, null);
            GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, READ_METHOD_NAME, readDescriptor);

            ga.visitCode();

            if (constructionMethod instanceof ClassModel.NoArgsConstructor) {
                ga.newInstance(sourceT);
                ga.dup();
                ga.visitMethodInsn(INVOKESPECIAL, sourceT.getInternalName(), ConstantDescs.INIT_NAME,
                        Type.getMethodDescriptor(Type.VOID_TYPE), false);
            } else if (constructionMethod instanceof ClassModel.CustomConstructor<?>) {
                classData.loadOnStack(thisT, ga, classData.constructorIdx());
                ga.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ClassModel.CustomConstructor.class),
                        "get", Type.getMethodDescriptor(Type.getType(Object.class)), true);
            } else {
                throw new IllegalStateException("Unexpected construction method");
            }

            for (Class<?> parent : attributes.keySet()) {
                ga.dup();
                classData.loadOnStack(thisT, ga, classData.parentAccessorIdx(parent));
                ga.swap();
                ga.loadArg(0);
                ga.swap();
                ga.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ObjectFactoryPart.class), READ_METHOD_NAME,
                        Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ModelDataContainer.class),
                                Type.getType(Object.class)), true);
            }

            ga.returnValue();
            ga.endMethod();
        }

        classData.visitFields(cw);
        classData.visitStaticBlock(thisT, cw);

        cw.visitEnd();

        try {
            Class<?> generated = defineClass(type, cw.toByteArray(), classData.asList());
            return (ObjectFactory<?>) generated.getDeclaredConstructor(ClassModel.class)
                    .newInstance(classModel);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException exception) {
            throw new RuntimeException(); // should not happen
        }
    }

    /**
     * Visits the constructor of the object factory, calling the parent constructor {@link #ObjectFactory(ClassModel)}.
     * <p>
     * This constructor will take one argument, passing it to the parent constructor.
     *
     * @param cv class visitor
     */
    private static void visitObjectFactoryConstructor(ClassVisitor cv) {
        var initDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ClassModel.class));
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, ConstantDescs.INIT_NAME, initDescriptor,
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(ObjectFactory.class), ConstantDescs.INIT_NAME,
                initDescriptor, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Visits the default no argument constructor, calling the super no arguments
     * constructor of provided parent class.
     *
     * @param cv class visitor
     * @param parent parent class
     */
    private static void visitDefaultConstructor(ClassVisitor cv, Type parent) {
        var initDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE);
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, ConstantDescs.INIT_NAME, initDescriptor,
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, parent.getInternalName(), ConstantDescs.INIT_NAME,
                initDescriptor, false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void visitReadForRecord(ClassVisitor cv, Type sourceT, List<ModelAttribute> attributes) {
        var readDescriptor = Type.getMethodDescriptor(Type.getType(Object.class),
                Type.getType(ModelDataContainer.class));
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, READ_METHOD_NAME, readDescriptor,
                null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, READ_METHOD_NAME, readDescriptor);

        ga.visitCode();

        ga.newInstance(sourceT);
        ga.dup();

        readFromContainer(ga, attributes);

        // call all args constructor
        Type[] allArgsParams = attributes.stream()
                .map(ModelAttribute::type)
                .map(Type::getType)
                .toArray(Type[]::new);
        ga.visitMethodInsn(INVOKESPECIAL, sourceT.getInternalName(), ConstantDescs.INIT_NAME,
                Type.getMethodDescriptor(Type.VOID_TYPE, allArgsParams), false);

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Creates the object factory part for given type with given attributes.
     *
     * @param type type to create the factory part for
     * @param includeWrite whether the object factory part should implement write operation
     * @param includeRead whether the object factory part should implement read operation
     * @param attributes attributes of the provided type
     * @return object factory type
     * @param <T> type
     */
    private static <T> ObjectFactoryPart<T> createObjectFactoryPart(Class<?> type,
                                                                    boolean includeWrite,
                                                                    boolean includeRead,
                                                                    List<ModelAttribute> attributes) {
        Type sourceT = Type.getType(type);
        Type thisT = Type.getObjectType(sourceT.getInternalName() + "$ObjectFactoryPart");

        ClassData.Builder builder = ClassData.builder();
        for (var attribute : attributes) {
            if (attribute.access().getter() instanceof AttributeAccess.CustomGetter<?>)
                builder.reserveGetter(attribute);
            if (attribute.access().setter() instanceof AttributeAccess.CustomSetter<?>)
                builder.reserveSetter(attribute);
        }
        ClassData classData = builder.build();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V25, ACC_PUBLIC, thisT.getInternalName(), null,
                Type.getInternalName(Object.class), new String[] { Type.getInternalName(ObjectFactoryPart.class) });

        visitDefaultConstructor(cw, Type.getType(Object.class));

        if (includeWrite) {
            visitObjectFactoryPartWrite(cw, sourceT, thisT, attributes, classData);
        } else {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, WRITE_METHOD_NAME, Type.getMethodDescriptor(Type.VOID_TYPE,
                    Type.getType(Object.class), Type.getType(ModelDataContainer.class)), null, null);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        if (includeRead) {
            visitObjectFactoryPartRead(cw, sourceT, thisT, attributes, classData);
        } else {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, READ_METHOD_NAME, Type.getMethodDescriptor(Type.VOID_TYPE,
                    Type.getType(ModelDataContainer.class), Type.getType(Object.class)), null, null);
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        classData.visitFields(cw);
        classData.visitStaticBlock(thisT, cw);

        cw.visitEnd();

        try {
            Class<?> generated = defineClass(type, cw.toByteArray(), classData.asList());
            //noinspection unchecked
            return (ObjectFactoryPart<T>) generated.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InstantiationException
                 | InvocationTargetException | IllegalAccessException exception) {
            throw new RuntimeException(); // should not happen
        }
    }

    /**
     *
     * Defines anonymous class with lookup in given target class with given data.
     * <p>
     * The defined class is hidden, defined in the same nest as {@code target}.
     *
     * @param target target class for the lookup
     * @param bytes class bytes
     * @param data class data for the class initialization
     * @return defined class
     */
    private static Class<?> defineClass(Class<?> target, byte[] bytes, Object data) {
        try {
            MethodHandles.Lookup targetLookup = MethodHandles.privateLookupIn(target, MethodHandles.lookup());
            return targetLookup.defineHiddenClassWithClassData(bytes, data, true,
                    MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Visits the object factory part write method.
     *
     * @param cv class visitor
     * @param sourceT the type of the class this object factory part is for
     * @param thisT the type of the class this method will be defined in
     * @param attributes list of attributes in the class model
     * @param classData class data containing the custom getters
     */
    private static void visitObjectFactoryPartWrite(ClassVisitor cv, Type sourceT, Type thisT,
                                                    List<ModelAttribute> attributes, ClassData classData) {
        var writeDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class),
                Type.getType(ModelDataContainer.class));
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, WRITE_METHOD_NAME, writeDescriptor,
                null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, WRITE_METHOD_NAME, writeDescriptor);

        ga.visitCode();

        for (ModelAttribute attribute : attributes) {
            ga.loadArg(1); // container
            ga.loadArg(0); // instance
            ga.checkCast(sourceT);

            switch (attribute.access().getter()) {
                case AttributeAccess.Direct(String name) ->
                        ga.getField(sourceT, name, Type.getType(attribute.type()));
                case AttributeAccess.Method(Class<?> returnType, String name, List<Class<?>> params) -> {
                    Type[] paramsArr = params.stream().map(Type::getType).toArray(Type[]::new);
                    boolean isInterface = attribute.source().isInterface();
                    int opcode = isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
                    ga.visitMethodInsn(opcode, sourceT.getInternalName(), name,
                            Type.getMethodDescriptor(Type.getType(returnType), paramsArr), isInterface);
                }
                case AttributeAccess.CustomGetter<?> custom -> {
                    classData.loadOnStack(thisT, ga, classData.getterIdx(attribute));
                    ga.swap();
                    Class<?> ownerType;
                    Type returnType;
                    switch (custom) {
                        case AttributeAccess.CustomGetter.Bool<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Bool.class; returnType = Type.BOOLEAN_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Char<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Char.class; returnType = Type.CHAR_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Byte<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Byte.class; returnType = Type.BYTE_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Short<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Short.class; returnType = Type.SHORT_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Int<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Int.class; returnType = Type.INT_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Long<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Long.class; returnType = Type.LONG_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Float<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Float.class; returnType = Type.FLOAT_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Double<?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Double.class; returnType = Type.DOUBLE_TYPE;
                        }
                        case AttributeAccess.CustomGetter.Object<?, ?> _ -> {
                            ownerType = AttributeAccess.CustomGetter.Object.class;
                            returnType = Type.getType(Object.class);
                        }
                    }
                    ga.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ownerType), "get",
                            Type.getMethodDescriptor(returnType, Type.getType(Object.class)), true);
                }
            }

            // write with the correct method
            String methodName;
            String methodDesc;
            Class<?> typeClass = attribute.type();

            if (typeClass == boolean.class) {
                methodName = "writeBool"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.BOOLEAN_TYPE);
            } else if (typeClass == char.class) {
                methodName = "writeChar"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.CHAR_TYPE);
            } else if (typeClass == byte.class) {
                methodName = "writeByte"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.BYTE_TYPE);
            } else if (typeClass == short.class) {
                methodName = "writeShort"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.SHORT_TYPE);
            } else if (typeClass == int.class) {
                methodName = "writeInt"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE);
            } else if (typeClass == long.class) {
                methodName = "writeLong"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.LONG_TYPE);
            } else if (typeClass == float.class) {
                methodName = "writeFloat"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.FLOAT_TYPE);
            } else if (typeClass == double.class) {
                methodName = "writeDouble"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE);
            } else {
                methodName = "writeObject"; methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE,
                        Type.getType(Object.class));
            }

            ga.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ModelDataContainer.class),
                    methodName, methodDesc, false);
        }

        ga.visitInsn(RETURN);
        ga.endMethod();
    }

    /**
     * Visits the object factory part read method.
     *
     * @param cv class visitor
     * @param sourceT the type of the class this object factory part is for
     * @param thisT the type of the class this method will be defined in
     * @param attributes list of attributes in the class model
     * @param classData class data containing the custom setters
     */
    private static void visitObjectFactoryPartRead(ClassVisitor cv, Type sourceT, Type thisT,
                                                   List<ModelAttribute> attributes, ClassData classData) {
        var readDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ModelDataContainer.class),
                Type.getType(Object.class));
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, READ_METHOD_NAME, readDescriptor,
                null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, READ_METHOD_NAME, readDescriptor);

        ga.visitCode();

        readFromContainer(ga, attributes);

        // set values individually
        List<ModelAttribute> onStack = new ArrayList<>(attributes);
        Collections.reverse(onStack);
        for (ModelAttribute attribute : onStack) {
            Type attributeSourceT = Type.getType(attribute.source());
            if (!(attribute.access().setter() instanceof AttributeAccess.CustomSetter<?>)) {
                ga.loadArg(1); // unfinished object instance
                ga.checkCast(sourceT);
                // swap with the value we did read earlier
                ASMUtil.swap(ga, 1, Type.getType(attribute.type()).getSize());
            }

            switch (attribute.access().setter()) {
                case AttributeAccess.Direct(String name) -> {
                    Type fieldType = Type.getType(attribute.type());
                    ga.putField(attributeSourceT, name, fieldType);
                }
                case AttributeAccess.Method(Class<?> returnType, String name, List<Class<?>> params) -> {
                    Type[] paramsArr = params.stream().map(Type::getType).toArray(Type[]::new);
                    boolean isInterface = attribute.source().isInterface();
                    int opcode = isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
                    ga.visitMethodInsn(opcode, attributeSourceT.getInternalName(), name,
                            Type.getMethodDescriptor(Type.getType(returnType), paramsArr), isInterface);
                    // we must pop whatever result we got
                    if (returnType != void.class) {
                        int size = Type.getType(returnType).getSize();
                        ASMUtil.pop(ga, size);
                    }
                }
                case AttributeAccess.CustomSetter<?> custom -> {
                    classData.loadOnStack(thisT, ga, classData.setterIdx(attribute));
                    ASMUtil.swap(ga, 1, Type.getType(attribute.type()).getSize());
                    ga.loadArg(1); // unfinished object instance
                    ASMUtil.swap(ga, 1, Type.getType(attribute.type()).getSize());
                    Class<?> ownerType;
                    Type valueType;
                    switch (custom) {
                        case AttributeAccess.CustomSetter.Bool<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Bool.class; valueType = Type.BOOLEAN_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Char<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Char.class; valueType = Type.CHAR_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Byte<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Byte.class; valueType = Type.BYTE_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Short<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Short.class; valueType = Type.SHORT_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Int<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Int.class; valueType = Type.INT_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Long<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Long.class; valueType = Type.LONG_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Float<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Float.class; valueType = Type.FLOAT_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Double<?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Double.class; valueType = Type.DOUBLE_TYPE;
                        }
                        case AttributeAccess.CustomSetter.Object<?, ?> _ -> {
                            ownerType = AttributeAccess.CustomSetter.Object.class;
                            valueType = Type.getType(Object.class);
                        }
                    }
                    ga.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ownerType), "set",
                            Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class), valueType),
                            true);
                }
                case null -> throw new IllegalStateException("Expected setter"); // should not happen
            }
        }

        ga.visitInsn(RETURN);
        ga.endMethod();
    }

    /**
     * Reads model attributes from container on stack.
     * This method expects the container to be as the first argument of the method.
     *
     * @param ga generator adapter
     * @param attributes attributes
     */
    private static void readFromContainer(GeneratorAdapter ga, List<ModelAttribute> attributes) {
        for (ModelAttribute attribute : attributes) {
            ga.loadArg(0); // container

            // read with the correct method
            String methodName;
            String methodDesc;
            Class<?> typeClass = attribute.type();

            if (typeClass == boolean.class) {
                methodName = "readBool"; methodDesc = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
            } else if (typeClass == char.class) {
                methodName = "readChar"; methodDesc = Type.getMethodDescriptor(Type.CHAR_TYPE);
            } else if (typeClass == byte.class) {
                methodName = "readByte"; methodDesc = Type.getMethodDescriptor(Type.BYTE_TYPE);
            } else if (typeClass == short.class) {
                methodName = "readShort"; methodDesc = Type.getMethodDescriptor(Type.SHORT_TYPE);
            } else if (typeClass == int.class) {
                methodName = "readInt"; methodDesc = Type.getMethodDescriptor(Type.INT_TYPE);
            } else if (typeClass == long.class) {
                methodName = "readLong"; methodDesc = Type.getMethodDescriptor(Type.LONG_TYPE);
            } else if (typeClass == float.class) {
                methodName = "readFloat"; methodDesc = Type.getMethodDescriptor(Type.FLOAT_TYPE);
            } else if (typeClass == double.class) {
                methodName = "readDouble"; methodDesc = Type.getMethodDescriptor(Type.DOUBLE_TYPE);
            } else {
                methodName = "readObject"; methodDesc = Type.getMethodDescriptor(Type.getType(Object.class));
            }

            ga.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(ModelDataContainer.class),
                    methodName, methodDesc, false);
            if (!attribute.primitive())
                ga.checkCast(Type.getType(attribute.type()));
        }
    }

}
