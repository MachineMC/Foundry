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

    final @Nullable ClassModel.CustomConstructor<?> constructor;
    final List<AttributeAccess> getters;
    final List<AttributeAccess> setters;
    final Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors;
    final boolean[] visited;

    private ClassData(@Nullable ClassModel.CustomConstructor<?> constructor,
                      List<AttributeAccess> getters, List<AttributeAccess> setters,
                      Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors) {
        this.constructor = constructor;
        this.getters = getters;
        this.setters = setters;
        this.parentAccessors = parentAccessors;
        int size = getters.size() + setters.size() + parentAccessors.size() + (constructor != null ? 1 : 0);
        this.visited = new boolean[size];
    }

    private int visit(int idx) {
        visited[idx] = true;
        return idx;
    }

    /**
     * @param access attribute access
     * @return index of getter for given attribute
     */
    int getterIdx(AttributeAccess access) {
        Preconditions.checkState(getters.contains(access), "Missing getter");
        return visit(getters.indexOf(access));
    }

    /**
     * @param access attribute access
     * @return index of setter for given attribute
     */
    int setterIdx(AttributeAccess access) {
        Preconditions.checkState(setters.contains(access), "Missing setter");
        return visit(getters.size() + setters.indexOf(access));
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
        return visit(getters.size() + setters.size() + i);
    }

    /**
     * @return constructor index
     */
    int constructorIdx() {
        Preconditions.checkState(constructor != null, "Missing constructor");
        return visit(getters.size() + setters.size() + parentAccessors.size());
    }

    /**
     * Visits the fields that have been requested by this instance of class data on given visitor.
     *
     * @param visitor visitor
     */
    void visitRequestedFields(ClassVisitor visitor) {
        int i = 0;
        for (var getter : getters)
            visitField(visitor, i++, getExactType(getter.getter()));
        for (var setter : setters) {
            Preconditions.checkNotNull(setter.setter(), "Expected setter but got null");
            visitField(visitor, i++, getExactType(setter.setter()));
        }
        for (var _ : parentAccessors.values())
            visitField(visitor, i++, ObjectFactory.ObjectFactoryPart.class);
        if (constructor != null)
            visitField(visitor, i, ClassModel.CustomConstructor.class);
    }

    private void visitField(ClassVisitor visitor, int idx, Class<?> fieldType) {
        if (!visited[idx])
            return;
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
            putField(owner, mv, i++, getExactType(getter.getter()));
        for (var setter : setters) {
            Preconditions.checkNotNull(setter.setter(), "Expected setter but got null");
            putField(owner, mv, i++, getExactType(setter.setter()));
        }
        for (var _ : parentAccessors.values())
            putField(owner, mv, i++, ObjectFactory.ObjectFactoryPart.class);
        if (constructor != null)
            putField(owner, mv, i, ClassModel.CustomConstructor.class);
    }

    private void putField(Type owner, MethodVisitor mv, int idx, Class<?> type) {
        if (!visited[idx])
            return;
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
     * Returns the class data as a list of objects that should be used in the class definition.
     *
     * @return class data as list
     * @see MethodHandles.Lookup#defineHiddenClassWithClassData(byte[], Object, boolean,
     * MethodHandles.Lookup.ClassOption...)
     */
    List<Object> asList() {
        Object[] data = new Object[visited.length];
        int offset = 0;
        for (var getter : getters)
            data[offset++] = getter.getter();
        for (var setter : setters)
            data[offset++] = setter.setter();
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
    static final class ClassDataBuilder {

        @Nullable ClassModel.CustomConstructor<?> constructor = null;
        final List<AttributeAccess> getters = new ArrayList<>();
        final List<AttributeAccess> setters = new ArrayList<>();
        final Map<Class<?>, ObjectFactory.ObjectFactoryPart<?>> parentAccessors = new LinkedHashMap<>();

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
         * @param access access for the attribute
         */
        void reserveGetter(AttributeAccess access) {
            if (getters.contains(access)) return;
            Preconditions.checkState(access.getter() instanceof AttributeAccess.CustomGetter<?>, "You can "
                    + "reserve getter place only for a custom getters");
            getters.add(access);
        }

        /**
         * Reserves new index for a custom setter.
         *
         * @param access access for the attribute
         */
        void reserveSetter(AttributeAccess access) {
            if (setters.contains(access)) return;
            Preconditions.checkState(access.setter() instanceof AttributeAccess.CustomSetter<?>, "You can "
                    + "reserve setter place only for a custom setter");
            setters.add(access);
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
