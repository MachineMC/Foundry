package org.machinemc.foundry.model;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

/**
 * Class responsible for generating {@link ObjectFactory} implementation
 * based on a provided {@link ClassModel}.
 */
final class ObjectFactoryGenerator {

    private static final String WRITE_METHOD_NAME = "write";
    private static final String READ_METHOD_NAME = "read";
    private static final String NEW_CONTAINER_METHOD_NAME = "newContainer";

    /**
     * Generates object factory for objects of given type using the given class model for
     * the object (de)construction.
     *
     * @param type type of the objects to (de)construct
     * @param classModel model to use for the (de)construction
     * @return object factory
     */
    static ObjectFactory<?> generate(Class<?> type, ClassModel<?> classModel) {
        ClassData.Builder classDataBuilder = ClassData.builder();
        ClassModel.ConstructionMethod constructionMethod = classModel.getConstructionMethod();

        if (constructionMethod instanceof ClassModel.CustomConstructor<?>
                || constructionMethod instanceof ClassModel.EnumConstructor<?>)
            classDataBuilder.reserveConstructor(constructionMethod);

        Map<Class<?>, List<ModelAttribute>> attributesByParent = Arrays.stream(classModel.getAttributes())
                .collect(Collectors.groupingBy(
                        ModelAttribute::source,
                        LinkedHashMap::new,
                        Collectors.toList()));

        attributesByParent.forEach((parent, parentAttributes) -> {
            // for records we do not generate the read implementation as all fields are set in the constructor
            // for enums we do not generate the read implementation as they are constants resolved by name
            boolean includeRead = !type.isRecord() && !type.isEnum();
            var part = ObjectFactoryPartGenerator.generatePart(parent, true, includeRead, parentAttributes);
            classDataBuilder.reserveParentAccessor(parent, part);
        });

        ClassData classData = classDataBuilder.build();

        Type sourceT = Type.getType(type);
        Type thisT = Type.getObjectType(sourceT.getInternalName() + "$ObjectFactory");

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V21, ACC_PUBLIC, thisT.getInternalName(), null,
                Type.getInternalName(ObjectFactory.class), new String[0]);

        visitConstructor(cw);
        visitWriteMethod(cw, thisT, attributesByParent, classData);

        if (type.isRecord()) {
            visitReadForRecord(cw, sourceT, List.of(classModel.getAttributes()));
        } else if (type.isEnum()) {
            visitReadForEnum(cw, sourceT, thisT, classModel.getAttributes()[0], classData);
        } else {
            visitReadMethod(cw, sourceT, thisT, constructionMethod, attributesByParent, classData);
        }

        classData.visitFields(cw);
        classData.visitStaticBlock(thisT, cw);
        cw.visitEnd();

        return defineAndInstantiate(type, cw.toByteArray(), classData, ClassModel.class, classModel);
    }

    /**
     * Visits the constructor of the object factory, calling the parent constructor
     * {@link ObjectFactory#ObjectFactory(ClassModel)}.
     * <p>
     * This constructor will take one argument, passing it to the parent constructor.
     *
     * @param cv class visitor
     */
    private static void visitConstructor(ClassVisitor cv) {
        var initDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ClassModel.class));
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, ConstantDescs.INIT_NAME, initDescriptor,
                null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, ACC_PUBLIC, ConstantDescs.INIT_NAME, initDescriptor);
        ga.visitCode();
        ga.loadThis();
        ga.loadArg(0);
        ga.invokeConstructor(Type.getType(ObjectFactory.class), new Method(ConstantDescs.INIT_NAME, initDescriptor));
        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory#write(Object)} method.
     *
     * @param cv class visitor
     * @param thisT type of the class this visitor is for
     * @param attributesByParent attributes mapped by parent classes
     * @param classData class data
     */
    private static void visitWriteMethod(ClassVisitor cv, Type thisT,
                                         Map<Class<?>, List<ModelAttribute>> attributesByParent,
                                         ClassData classData) {
        Method writeMethod = new Method(WRITE_METHOD_NAME, Type.getType(ModelDataContainer.class),
                new Type[]{Type.getType(Object.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, writeMethod, null, null, cv);
        ga.visitCode();

        ga.loadThis();
        ga.invokeVirtual(Type.getType(ObjectFactory.class), new Method(NEW_CONTAINER_METHOD_NAME,
                Type.getMethodDescriptor(Type.getType(ModelDataContainer.class))));

        for (Class<?> parent : attributesByParent.keySet()) {
            ga.dup();
            classData.loadOnStack(thisT, ga, classData.parentAccessorIdx(parent));
            ga.swap();
            ga.loadArg(0);
            ga.swap();
            ga.invokeInterface(Type.getType(ObjectFactory.ObjectFactoryPart.class),
                    new Method(WRITE_METHOD_NAME, Type.VOID_TYPE, new Type[]{Type.getType(Object.class),
                            Type.getType(ModelDataContainer.class)}));
        }

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory#read(ModelDataContainer)} method.
     *
     * @param cv class visitor
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param constructionMethod construction method used by the class model
     * @param attributesByParent attributes mapped by parent classes
     * @param classData class data
     */
    private static void visitReadMethod(ClassVisitor cv, Type sourceT, Type thisT,
                                        ClassModel.ConstructionMethod constructionMethod,
                                        Map<Class<?>, List<ModelAttribute>> attributesByParent,
                                        ClassData classData) {
        Method readMethod = new Method(READ_METHOD_NAME, Type.getType(Object.class),
                new Type[]{Type.getType(ModelDataContainer.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, readMethod, null, null, cv);
        ga.visitCode();

        if (constructionMethod instanceof ClassModel.NoArgsConstructor) {
            ga.newInstance(sourceT);
            ga.dup();
            ga.invokeConstructor(sourceT, new Method(ConstantDescs.INIT_NAME, Type.VOID_TYPE, new Type[0]));
        } else if (constructionMethod instanceof ClassModel.CustomConstructor<?>) {
            classData.loadOnStack(thisT, ga, classData.constructorIdx());
            ga.invokeInterface(Type.getType(ClassModel.CustomConstructor.class),
                    new Method("get", Type.getType(Object.class), new Type[0]));
        }

        for (Class<?> parent : attributesByParent.keySet()) {
            ga.dup();
            classData.loadOnStack(thisT, ga, classData.parentAccessorIdx(parent));
            ga.swap();
            ga.loadArg(0);
            ga.swap();
            ga.invokeInterface(Type.getType(ObjectFactory.ObjectFactoryPart.class),
                    new Method(READ_METHOD_NAME, Type.VOID_TYPE, new Type[]{Type.getType(ModelDataContainer.class),
                            Type.getType(Object.class)}));
        }

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory#read(ModelDataContainer)} method for record classes.
     * <p>
     * For records this method must call the all argument canonical constructor.
     *
     * @param cv class visitor
     * @param sourceT type of the class this factory is for
     * @param attributes attributes of the class model (record components)
     */
    private static void visitReadForRecord(ClassVisitor cv, Type sourceT, List<ModelAttribute> attributes) {
        Method readMethod = new Method(READ_METHOD_NAME, Type.getType(Object.class),
                new Type[]{Type.getType(ModelDataContainer.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, readMethod, null, null, cv);
        ga.visitCode();

        ga.newInstance(sourceT);
        ga.dup();

        for (ModelAttribute attribute : attributes) {
            ga.loadArg(0);
            ObjectFactoryPartGenerator.visitReadFromContainer(ga, attribute);
        }

        Type[] allArgsParams = attributes.stream()
                .map(ModelAttribute::type)
                .map(Type::getType)
                .toArray(Type[]::new);

        ga.invokeConstructor(sourceT, new Method(ConstantDescs.INIT_NAME, Type.VOID_TYPE, allArgsParams));
        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory#read(ModelDataContainer)} method for enum classes.
     * <p>
     * For enums this is resolved by the first object element in the container which is always guaranteed to be the
     * name of the enum constant.
     *
     * @param cv class visitor
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param nameAttribute attribute of the enum name
     * @param classData class data
     */
    private static void visitReadForEnum(ClassVisitor cv, Type sourceT, Type thisT, ModelAttribute nameAttribute,
                                         ClassData classData) {
        Method readMethod = new Method(READ_METHOD_NAME, Type.getType(Object.class),
                new Type[]{Type.getType(ModelDataContainer.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, readMethod, null, null, cv);
        ga.visitCode();

        ga.loadArg(0);
        ObjectFactoryPartGenerator.visitReadFromContainer(ga, nameAttribute);

        classData.loadOnStack(thisT, ga, classData.constructorIdx());
        ga.swap();
        ga.invokeInterface(Type.getType(ClassModel.EnumConstructor.class),
                new Method("get", Type.getType(Enum.class), new Type[] {Type.getType(String.class)}));
        ga.checkCast(sourceT);

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Defines new anonymous nestmate class using private lookup in {@code target} with given data.
     * <p>
     * Calls its no arguments constructor and returns the instance.
     *
     * @param target target class
     * @param bytes bytes (class data)
     * @param classData class data passed to the class initialization
     * @return instance
     * @param <T> type
     */
    static <T> T defineAndInstantiate(Class<?> target, byte[] bytes, ClassData classData) {
        return defineAndInstantiate(target, bytes, classData, new Class<?>[0]);
    }

    /**
     * Defines new anonymous nestmate class using private lookup in {@code target} with given data.
     * <p>
     * Calls its single argument constructor and returns the instance.
     *
     * @param target target class
     * @param bytes bytes (class data)
     * @param classData class data passed to the class initialization
     * @param paramType type in the constructor
     * @param paramValue value to call the constructor with
     * @return instance
     * @param <T> type
     */
    static <T> T defineAndInstantiate(Class<?> target, byte[] bytes, ClassData classData,
                                      Class<?> paramType, Object paramValue) {
        return defineAndInstantiate(target, bytes, classData, new Class<?>[]{paramType}, paramValue);
    }

    /**
     * Defines new anonymous nestmate class using private lookup in {@code target} with given data.
     * <p>
     * Calls its constructor and returns the instance.
     *
     * @param target target class
     * @param bytes bytes (class data)
     * @param classData class data passed to the class initialization
     * @param paramTypes constructor parameters
     * @param args arguments to call the constructor with
     * @return instance
     * @param <T> type
     */
    static <T> T defineAndInstantiate(Class<?> target, byte[] bytes, ClassData classData,
                                      Class<?>[] paramTypes, Object... args) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(target, MethodHandles.lookup());
            Class<?> generated = lookup.defineHiddenClassWithClassData(bytes, classData.asList(), true,
                    MethodHandles.Lookup.ClassOption.NESTMATE).lookupClass();

            Constructor<?> constructor = generated.getDeclaredConstructor(paramTypes);
            //noinspection unchecked
            return (T) constructor.newInstance(args);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to define and instantiate generated class for " + target.getName(),
                    exception);
        }
    }

}
