package org.machinemc.foundry.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.util.ASMUtil;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.StaticInitMerger;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

/**
 * Utility for handling the class data for definition of the
 * object factory classes.
 */
final class ClassData {

    /**
     * @return new builder for class data
     */
    static Builder builder() {
        return new Builder();
    }

    private final @Nullable ClassModel.CustomConstructor<?> constructor;
    private final List<ModelAttribute> getters;
    private final List<ModelAttribute> setters;
    private final Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors;
    private final int size;

    private ClassData(@Nullable ClassModel.CustomConstructor<?> constructor,
                      List<ModelAttribute> getters, List<ModelAttribute> setters,
                      Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors) {
        this.constructor = constructor;
        this.getters = getters;
        this.setters = setters;
        this.parentAccessors = parentAccessors;
        size = getters.size() + setters.size() + parentAccessors.size() + (constructor != null ? 1 : 0);
    }

    /**
     * @param attribute attribute
     * @return index of getter for given attribute
     */
    int getterIdx(ModelAttribute attribute) {
        Preconditions.checkState(getters.contains(attribute), "Missing getter");
        return getters.indexOf(attribute);
    }

    /**
     * @param attribute attribute
     * @return index of setter for given attribute
     */
    int setterIdx(ModelAttribute attribute) {
        Preconditions.checkState(setters.contains(attribute), "Missing setter");
        return getters.size() + setters.indexOf(attribute);
    }

    /**
     * @param parent parent class
     * @return index of parent accessor for given parent class
     */
    int parentAccessorIdx(Class<?> parent) {
        Preconditions.checkState(parentAccessors.containsKey(parent), "Missing parent accessor");
        int i = 0;
        for (var key : parentAccessors.keySet()) {
            if (parent.equals(key)) break;
            i++;
        }
        return getters.size() + setters.size() + i;
    }

    /**
     * @return constructor index
     */
    int constructorIdx() {
        Preconditions.checkState(constructor != null, "Missing constructor");
        return getters.size() + setters.size() + parentAccessors.size();
    }

    /**
     * Visits the fields of class data on given visitor.
     *
     * @param visitor visitor
     */
    void visitFields(ClassVisitor visitor) {
        int i = 0;
        for (var getter : getters)
            visitField(visitor, i++, getExactType(getter.access().getter()));
        for (var setter : setters) {
            Preconditions.checkNotNull(setter.access().setter(), "Expected setter but got null");
            visitField(visitor, i++, getExactType(setter.access().setter()));
        }
        for (var _ : parentAccessors.values())
            visitField(visitor, i++, ObjectFactory.ObjectFactoryPart.class);
        if (constructor != null)
            visitField(visitor, i, ClassModel.CustomConstructor.class);
    }

    private void visitField(ClassVisitor visitor, int idx, Class<?> fieldType) {
        visitor.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, fieldNameOf(idx),
                Type.getDescriptor(fieldType), null, null);
    }

    /**
     * Visits the static block to initialize the class data values.
     * <p>
     * This method also creates the class init method. If you do not want that, use
     * either {@link #visitStaticBlock(Type, MethodVisitor)} or wrap your class
     * visitor with {@link StaticInitMerger}.
     *
     * @param owner owner type of the fields to initialize in the static block
     * @param classVisitor class visitor
     */
    void visitStaticBlock(Type owner, ClassVisitor classVisitor) {
        String classInitDesc = Type.getMethodDescriptor(Type.VOID_TYPE);
        MethodVisitor mv = classVisitor.visitMethod(ACC_PUBLIC | ACC_STATIC, ConstantDescs.CLASS_INIT_NAME,
                classInitDesc, null, null);
        mv.visitCode();
        visitStaticBlock(owner, mv);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Visits the static block to initialize the class data values.
     *
     * @param owner owner type of the fields to initialize in the static block
     * @param mv method visitor
     */
    void visitStaticBlock(Type owner, MethodVisitor mv) {
        int i = 0;
        for (var getter : getters)
            putField(owner, mv, i++, getExactType(getter.access().getter()));
        for (var setter : setters) {
            Preconditions.checkNotNull(setter.access().setter(), "Expected setter but got null");
            putField(owner, mv, i++, getExactType(setter.access().setter()));
        }
        for (var _ : parentAccessors.values())
            putField(owner, mv, i++, ObjectFactory.ObjectFactoryPart.class);
        if (constructor != null)
            putField(owner, mv, i, ClassModel.CustomConstructor.class);
    }

    private void putField(Type owner, MethodVisitor mv, int idx, Class<?> type) {
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MethodHandles.class), "lookup",
                Type.getMethodDescriptor(Type.getType(MethodHandles.Lookup.class)), false);
        mv.visitLdcInsn(ConstantDescs.DEFAULT_NAME);
        mv.visitLdcInsn(Type.getType(type));
        ASMUtil.push(mv, idx);
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(MethodHandles.class), "classDataAt",
                Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(MethodHandles.Lookup.class),
                        Type.getType(String.class), Type.getType(Class.class), Type.INT_TYPE), false);
        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(type));
        mv.visitFieldInsn(PUTSTATIC, owner.getInternalName(), fieldNameOf(idx), Type.getDescriptor(type));
    }

    /**
     * Returns the field name of the field holding data with given index.
     *
     * @param idx index
     * @return field name
     */
    String fieldNameOf(int idx) {
        return "data_" + idx;
    }

    /**
     * Loads the data with given index on the top of the stack.
     *
     * @param owner current class visited by the method visitor
     * @param mv method visitor
     * @param idx index
     */
    void loadOnStack(Type owner, MethodVisitor mv, int idx) {
        Class<?> type;
        // getters
        if (idx < getters.size()) {
            type = getExactType(getters.get(idx).access().getter());
        }
        // setters
        else if (idx < getters.size() + setters.size()) {
            int setterIdx = idx - getters.size();
            var setter = setters.get(setterIdx).access().setter();
            Preconditions.checkNotNull(setter, "Expected setter");
            type = getExactType(setter);
        }
        // parent accessors
        else if (idx < getters.size() + setters.size() + parentAccessors.size()) {
            type = ObjectFactory.ObjectFactoryPart.class;
        }
        // constructor
        else if (constructor != null && idx == constructorIdx()) {
            type = ClassModel.CustomConstructor.class;
        }
        else {
            throw new ArrayIndexOutOfBoundsException("Index " + idx + " is out of bounds for size " + size);
        }
        mv.visitFieldInsn(GETSTATIC, owner.getInternalName(), fieldNameOf(idx), Type.getDescriptor(type));
    }

    /**
     * Returns the class data as a list of objects that should be used in the class definition.
     *
     * @return class data as list
     * @see MethodHandles.Lookup#defineHiddenClassWithClassData(byte[], Object, boolean,
     * MethodHandles.Lookup.ClassOption...)
     */
    List<Object> asList() {
        Object[] data = new Object[size];
        int offset = 0;
        for (var getter : getters)
            data[offset++] = getter.access().getter();
        for (var setter : setters)
            data[offset++] = setter.access().setter();
        for (var accessor : parentAccessors.values())
            data[offset++] = accessor;
        if (constructor != null)
            data[offset] = constructor;
        return List.of(data);
    }

    private static Class<?> getExactType(AttributeAccess.Get getter) {
        return switch (getter) {
            case AttributeAccess.CustomGetter.Bool<?> _ -> AttributeAccess.CustomGetter.Bool.class;
            case AttributeAccess.CustomGetter.Char<?> _ -> AttributeAccess.CustomGetter.Char.class;
            case AttributeAccess.CustomGetter.Byte<?> _ -> AttributeAccess.CustomGetter.Byte.class;
            case AttributeAccess.CustomGetter.Short<?> _ -> AttributeAccess.CustomGetter.Short.class;
            case AttributeAccess.CustomGetter.Int<?> _ -> AttributeAccess.CustomGetter.Int.class;
            case AttributeAccess.CustomGetter.Long<?> _ -> AttributeAccess.CustomGetter.Long.class;
            case AttributeAccess.CustomGetter.Float<?> _ -> AttributeAccess.CustomGetter.Float.class;
            case AttributeAccess.CustomGetter.Double<?> _ -> AttributeAccess.CustomGetter.Double.class;
            case AttributeAccess.CustomGetter.Object<?, ?> _ -> AttributeAccess.CustomGetter.Object.class;
            default -> throw new IllegalStateException("Unexpected getter implementation");
        };
    }

    private static Class<?> getExactType(AttributeAccess.Set setter) {
        return switch (setter) {
            case AttributeAccess.CustomSetter.Bool<?> _ -> AttributeAccess.CustomSetter.Bool.class;
            case AttributeAccess.CustomSetter.Char<?> _ -> AttributeAccess.CustomSetter.Char.class;
            case AttributeAccess.CustomSetter.Byte<?> _ -> AttributeAccess.CustomSetter.Byte.class;
            case AttributeAccess.CustomSetter.Short<?> _ -> AttributeAccess.CustomSetter.Short.class;
            case AttributeAccess.CustomSetter.Int<?> _ -> AttributeAccess.CustomSetter.Int.class;
            case AttributeAccess.CustomSetter.Long<?> _ -> AttributeAccess.CustomSetter.Long.class;
            case AttributeAccess.CustomSetter.Float<?> _ -> AttributeAccess.CustomSetter.Float.class;
            case AttributeAccess.CustomSetter.Double<?> _ -> AttributeAccess.CustomSetter.Double.class;
            case AttributeAccess.CustomSetter.Object<?, ?> _ -> AttributeAccess.CustomSetter.Object.class;
            default -> throw new IllegalStateException("Unexpected setter implementation");
        };
    }

    /**
     * Builder for class data instances.
     */
    static final class Builder {

        private Builder() {
        }

        private @Nullable ClassModel.CustomConstructor<?> constructor = null;
        private final List<ModelAttribute> getters = new ArrayList<>();
        private final List<ModelAttribute> setters = new ArrayList<>();
        private final Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors = new LinkedHashMap<>();

        /**
         * Reserves new index for a custom constructor.
         *
         * @param constructor constructor
         */
        void reserveConstructor(ClassModel.CustomConstructor<?> constructor) {
            Preconditions.checkState(this.constructor == null, "You can reserve place only for "
                    + "a single constructor");
            this.constructor = constructor;
        }

        /**
         * Reserves new index for a custom getter.
         *
         * @param attribute attribute to reserve the getter for
         */
        void reserveGetter(ModelAttribute attribute) {
            if (getters.contains(attribute)) return;
            Preconditions.checkState(attribute.access().getter() instanceof AttributeAccess.CustomGetter<?>,
                    "You can reserve getter place only for a custom getters");
            getters.add(attribute);
        }

        /**
         * Reserves new index for a custom setter.
         *
         * @param attribute attribute to reserve the setter for
         */
        void reserveSetter(ModelAttribute attribute) {
            if (setters.contains(attribute)) return;
            Preconditions.checkState(attribute.access().setter() instanceof AttributeAccess.CustomSetter<?>,
                    "You can reserve setter place only for a custom setter");
            setters.add(attribute);
        }

        /**
         * Reserves new index for a parent accessor.
         *
         * @param parent parent class
         * @param instance accessor instance
         */
        void reserveParentAccessor(Class<?> parent, ObjectFactory.ObjectFactoryPart<?> instance) {
            Preconditions.checkState(!parentAccessors.containsKey(parent), "There is already a reserved "
                    + "place for '" + parent.getName() + "'");
            parentAccessors.put(parent, instance);
        }

        /**
         * @return new class data instance for this builder
         */
        ClassData build() {
            return new ClassData(
                    constructor,
                    ImmutableList.copyOf(getters),
                    ImmutableList.copyOf(setters),
                    ImmutableMap.copyOf(parentAccessors)
            );
        }

    }

}
