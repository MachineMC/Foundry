package org.machinemc.foundry.model;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        Type sourceT = Type.getType(type);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V25, ACC_PUBLIC, sourceT.getInternalName() + "$ModelData", null,
                Type.getInternalName(ObjectFactory.class), new String[0]);

        // constructor
        {
            var initDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ClassModel.class));
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, ConstantDescs.INIT_NAME, initDescriptor,
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

            for (ModelAttribute attribute : classModel.getAttributes()) {
                Type attributeSourceT = Type.getType(attribute.source());
                // prepare the container for write
                ga.dup();
                // load the value to write into the container
                ga.loadArg(0);
                ga.checkCast(attributeSourceT);
                switch (attribute.access().getter()) {
                    case AttributeAccess.Direct(String name) ->
                            ga.getField(attributeSourceT, name, Type.getType(attribute.type()));
                    case AttributeAccess.Method(Class<?> returnType, String name, List<Class<?>> params) -> {
                        Type[] paramsArr = params.stream().map(Type::getType).toArray(Type[]::new);
                        boolean isInterface = attribute.source().isInterface();
                        int opcode = isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
                        ga.visitMethodInsn(opcode, attributeSourceT.getInternalName(), name,
                                Type.getMethodDescriptor(Type.getType(returnType), paramsArr), isInterface);
                    }
                    default -> throw new IllegalStateException(); // TODO custom getters
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

            ga.returnValue();
            ga.endMethod();
        }

        // read
        {
            var readDescriptor = Type.getMethodDescriptor(Type.getType(Object.class),
                    Type.getType(ModelDataContainer.class));
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, READ_METHOD_NAME, readDescriptor,
                    null, null);
            GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, READ_METHOD_NAME, readDescriptor);

            ga.visitCode();

            if (classModel.getConstructionMethod() instanceof ClassModel.AllArgsConstructor) {
                ga.newInstance(sourceT);
                ga.dup();
            }

            for (ModelAttribute attribute : classModel.getAttributes()) {
                // load the container for read
                ga.loadArg(0);

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

            // call all args constructor
            if (classModel.getConstructionMethod() instanceof ClassModel.AllArgsConstructor) {
                Type[] allArgsParams = Arrays.stream(classModel.getAttributes())
                        .map(ModelAttribute::type)
                        .map(Type::getType)
                        .toArray(Type[]::new);
                mv.visitMethodInsn(INVOKESPECIAL, sourceT.getInternalName(), ConstantDescs.INIT_NAME,
                        Type.getMethodDescriptor(Type.VOID_TYPE, allArgsParams), false);
            }
            // call no args constructor and set the values individually
            else {
                ga.newInstance(sourceT);
                ga.dup();
                mv.visitMethodInsn(INVOKESPECIAL, sourceT.getInternalName(), ConstantDescs.INIT_NAME,
                        Type.getMethodDescriptor(Type.VOID_TYPE), false);
                int instanceVar = ga.newLocal(sourceT);
                ga.storeLocal(instanceVar);
                List<ModelAttribute> onStack = Arrays.asList(classModel.getAttributes());
                Collections.reverse(onStack);
                for (ModelAttribute attribute : onStack) {
                    ga.loadLocal(instanceVar); // load the unfinished object instance
                    Type attributeSourceT = Type.getType(attribute.source());
                    // swap with the value we did read earlier
                    if (attribute.type() == long.class || attribute.type() == double.class) {
                        ga.dupX2();
                        ga.pop();
                    } else {
                        ga.swap();
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
                                ga.pop();
                            }
                        }
                        case null -> throw new IllegalStateException("Expected setter"); // should not happen
                        default -> throw new IllegalStateException(); // TODO custom setters
                    }
                }
                ga.loadLocal(instanceVar); // load the finished instance to return
            }

            ga.returnValue();
            ga.endMethod();
        }

        cw.visitEnd();
        byte[] classData = cw.toByteArray();

        try {
            MethodHandles.Lookup targetLookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            Class<?> generated = targetLookup.defineHiddenClass(classData, false,
                            MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();
            return (ObjectFactory<?>) generated.getDeclaredConstructor(ClassModel.class)
                    .newInstance(classModel);
        } catch (IllegalAccessException exception) {
            throw new RuntimeException(exception);
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException exception) {
            throw new RuntimeException(); // should not happen
        }
    }

}
