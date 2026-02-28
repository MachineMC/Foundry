package org.machinemc.foundry.model;

import org.machinemc.foundry.util.ASMUtil;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Class responsible for generating {@link ObjectFactory.ObjectFactoryPart} implementation
 * based on a provided {@link ClassModel}.
 */
final class ObjectFactoryPartGenerator {

    /**
     * Generates object factory part for objects of given parent type.
     * <p>
     * This object factory part (de)constructs all attributes within this parent part.
     *
     * @param type type of the parent, containing the attributes to access
     * @param includeWrite whether the {@link ObjectFactory.ObjectFactoryPart#write(Object)} should be implemented
     * @param includeRead whether the {@link ObjectFactory.ObjectFactoryPart#read(ModelDataContainer)} should be
     *                    implemented, this is {@code false} for records, as their attributes get set during the
     *                    creation, not after, and also for enums, as they are constants resolved by name.
     * @param attributes attributes to access in this part (attributes of the parent class)
     * @return object factory
     */
    static <T> ObjectFactory.ObjectFactoryPart<T> generatePart(Class<?> type,
                                                               boolean includeWrite, boolean includeRead,
                                                               List<ModelAttribute> attributes) {
        Type sourceT = Type.getType(type);
        Type thisT = Type.getObjectType(sourceT.getInternalName() + "$ObjectFactoryPart");

        Class<?> target = type;
        String moduleName = target.getModule().getName();
        if (moduleName != null && moduleName.equals("java.base")) {
            // java.base is closed to us, we use one of our classes
            // and just expect we do not access internals.
            // this happens e.g. with enums for their name and ordinal fields
            target = ObjectFactoryPartGenerator.class;
            thisT = Type.getObjectType(Type.getInternalName(ObjectFactoryPartGenerator.class)
                    + "$ObjectFactoryPart");
        }

        ClassData.Builder builder = ClassData.builder();
        for (var attribute : attributes) {
            if (attribute.access().getter() instanceof AttributeAccess.CustomGetter<?>)
                builder.reserveGetter(attribute);
            if (attribute.access().setter() instanceof AttributeAccess.CustomSetter<?>)
                builder.reserveSetter(attribute);
        }
        ClassData classData = builder.build();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V21, ACC_PUBLIC, thisT.getInternalName(), null, Type.getInternalName(Object.class),
                new String[]{Type.getInternalName(ObjectFactory.ObjectFactoryPart.class)});

        visitDefaultConstructor(cw);

        if (includeWrite) {
            visitWriteMethod(cw, sourceT, thisT, attributes, classData);
        } else {
            visitEmptyMethod(cw, "write", Type.VOID_TYPE, new Type[]{Type.getType(Object.class),
                    Type.getType(ModelDataContainer.class)});
        }

        if (includeRead) {
            visitReadMethod(cw, sourceT, thisT, attributes, classData);
        } else {
            visitEmptyMethod(cw, "read", Type.VOID_TYPE, new Type[]{Type.getType(ModelDataContainer.class),
                    Type.getType(Object.class)});
        }

        classData.visitFields(cw);
        classData.visitStaticBlock(thisT, cw);
        cw.visitEnd();

        return ObjectFactoryGenerator.defineAndInstantiate(target, cw.toByteArray(), classData);
    }

    /**
     * Visits the default constructor (no arguments) with Object as the class with super constructor.
     *
     * @param cv class visitor
     */
    private static void visitDefaultConstructor(ClassVisitor cv) {
        Method init = new Method(ConstantDescs.INIT_NAME, Type.VOID_TYPE, new Type[0]);
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, init, null, null, cv);
        ga.visitCode();
        ga.loadThis();
        ga.invokeConstructor(Type.getType(Object.class), init);
        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits a method and does nothing; making this method implementation empty.
     *
     * @param cv class visitor
     * @param name name of the method
     * @param returnType return type of the method
     * @param args parameter types of the method
     */
    private static void visitEmptyMethod(ClassVisitor cv, String name, Type returnType, Type[] args) {
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, new Method(name, returnType, args), null, null, cv);
        ga.visitCode();
        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory.ObjectFactoryPart#write(Object, ModelDataContainer)} method.
     *
     * @param cv class visitor
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param attributes attributes to write
     * @param classData class data
     */
    private static void visitWriteMethod(ClassVisitor cv, Type sourceT, Type thisT,
                                         List<ModelAttribute> attributes, ClassData classData) {
        Method write = new Method("write", Type.VOID_TYPE, new Type[]{Type.getType(Object.class),
                Type.getType(ModelDataContainer.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, write, null, null, cv);
        ga.visitCode();

        for (ModelAttribute attribute : attributes) {
            ga.loadArg(1);
            ga.loadArg(0);
            ga.checkCast(sourceT);

            visitLoadValueFromInstance(ga, sourceT, thisT, attribute, classData);
            visitWriteToContainer(ga, attribute);
        }

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Visits the {@link ObjectFactory.ObjectFactoryPart#read(ModelDataContainer, Object)} method.
     *
     * @param cv class visitor
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param attributes attributes to write
     * @param classData class data
     */
    private static void visitReadMethod(ClassVisitor cv, Type sourceT, Type thisT,
                                        List<ModelAttribute> attributes, ClassData classData) {
        Method read = new Method("read", Type.VOID_TYPE,
                new Type[]{Type.getType(ModelDataContainer.class),Type.getType(Object.class)});
        GeneratorAdapter ga = new GeneratorAdapter(ACC_PUBLIC, read, null, null, cv);
        ga.visitCode();

        for (ModelAttribute attribute : attributes) {
            ga.loadArg(0);
            visitReadFromContainer(ga, attribute);
        }

        List<ModelAttribute> onStack = new ArrayList<>(attributes);
        Collections.reverse(onStack);

        for (ModelAttribute attribute : onStack) {
            Type attributeSourceT = Type.getType(attribute.source());
            Type fieldType = Type.getType(attribute.type());

            if (!(attribute.access().setter() instanceof AttributeAccess.CustomSetter<?>)) {
                ga.loadArg(1);
                ga.checkCast(sourceT);
                ASMUtil.swap(ga, 1, fieldType.getSize());
            }

            visitStoreValueToInstance(ga, attributeSourceT, thisT, attribute, classData);
        }

        ga.returnValue();
        ga.endMethod();
    }

    /**
     * Loads a value of given attribute depending on its getter.
     *
     * @param ga generator adapter
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param attribute attribute to load
     * @param classData class data
     */
    private static void visitLoadValueFromInstance(GeneratorAdapter ga, Type sourceT, Type thisT,
                                                   ModelAttribute attribute, ClassData classData) {
        switch (attribute.access().getter()) {
            case AttributeAccess.Direct(String name) -> ga.getField(sourceT, name, Type.getType(attribute.type()));
            case AttributeAccess.Method(Class<?> returnType, String name, List<Class<?>> params) -> {
                Type[] paramsArr = params.stream().map(Type::getType).toArray(Type[]::new);
                Method method = new Method(name, Type.getType(returnType), paramsArr);
                if (attribute.source().isInterface()) ga.invokeInterface(sourceT, method);
                else ga.invokeVirtual(sourceT, method);
            }
            case AttributeAccess.CustomGetter<?> _ -> {
                classData.loadOnStack(thisT, ga, classData.getterIdx(attribute));
                ga.swap();
                ContainerTypeMapping mapping = ContainerTypeMapping.of(attribute.type());
                ga.invokeInterface(Type.getType(mapping.customGetter), new Method("get", mapping.asmType,
                        new Type[]{Type.getType(Object.class)}));
            }
        }
    }

    /**
     * Writes the value of attribute to a model data container.
     *
     * @param ga generator adapter
     * @param attribute model attribute to write
     */
    private static void visitWriteToContainer(GeneratorAdapter ga, ModelAttribute attribute) {
        ContainerTypeMapping mapping = ContainerTypeMapping.of(attribute.type());
        Method writeMethod = new Method(mapping.writeMethod, Type.VOID_TYPE, new Type[]{mapping.asmType});
        ga.invokeVirtual(Type.getType(ModelDataContainer.class), writeMethod);
    }

    /**
     * Reads the value of attribute from a model data container.
     *
     * @param ga generator adapter
     * @param attribute model attribute to read
     */
    static void visitReadFromContainer(GeneratorAdapter ga, ModelAttribute attribute) {
        ContainerTypeMapping mapping = ContainerTypeMapping.of(attribute.type());
        Method readMethod = new Method(mapping.readMethod, mapping.asmType, new Type[0]);
        ga.invokeVirtual(Type.getType(ModelDataContainer.class), readMethod);
        if (!attribute.primitive()) {
            ga.checkCast(Type.getType(attribute.type()));
        }
    }

    /**
     * Stores the value on the object instance (writes the value from container to the unfinished object).
     *
     * @param ga generator adapter
     * @param sourceT type of the class this factory is for
     * @param thisT type of the class this visitor is for
     * @param attribute attribute to set to the instance
     * @param classData class data
     */
    private static void visitStoreValueToInstance(GeneratorAdapter ga, Type sourceT, Type thisT,
                                                  ModelAttribute attribute, ClassData classData) {
        Type fieldType = Type.getType(attribute.type());
        switch (attribute.access().setter()) {
            case AttributeAccess.Direct(String name) -> ga.putField(sourceT, name, fieldType);
            case AttributeAccess.Method(Class<?> returnType, String name, List<Class<?>> params) -> {
                Type[] paramsArr = params.stream().map(Type::getType).toArray(Type[]::new);
                Method method = new Method(name, Type.getType(returnType), paramsArr);
                if (attribute.source().isInterface()) ga.invokeInterface(sourceT, method);
                else ga.invokeVirtual(sourceT, method);
                if (returnType != void.class) ASMUtil.pop(ga, Type.getType(returnType).getSize());
            }
            case AttributeAccess.CustomSetter<?> _ -> {
                classData.loadOnStack(thisT, ga, classData.setterIdx(attribute));
                ASMUtil.swap(ga, 1, fieldType.getSize());
                ga.loadArg(1);
                ASMUtil.swap(ga, 1, fieldType.getSize());
                ContainerTypeMapping mapping = ContainerTypeMapping.of(attribute.type());
                ga.invokeInterface(Type.getType(mapping.customSetter), new Method("set", Type.VOID_TYPE,
                        new Type[]{Type.getType(Object.class), mapping.asmType}));
            }
            case null -> throw new IllegalStateException("Expected setter for " + attribute.name());
        }
    }

}
