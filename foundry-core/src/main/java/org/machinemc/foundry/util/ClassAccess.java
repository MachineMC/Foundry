package org.machinemc.foundry.util;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.signature.SignatureWriter;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ClassAccess {

    private static final Map<FieldAccessKey<?>, FieldAccess<?, ?>> fieldCache = new ConcurrentHashMap<>();
    private static final Map<MethodAccessKey<?>, MethodAccess<?, ?>> methodCache = new ConcurrentHashMap<>();

    private ClassAccess() {}

    public static <T> T getField(Object source, String name) throws NoSuchFieldException {
        //noinspection unchecked,rawtypes
        return (T) ((FieldAccess) field(source.getClass(), name)).get(source);
    }

    public static <T> void setField(Object source, String name, T value) throws NoSuchFieldException {
        //noinspection unchecked,rawtypes
        ((FieldAccess) field(source.getClass(), name)).set(source, value);
    }

    public static <T> T getFieldStatic(Class<?> source, String name) throws NoSuchFieldException {
        // noinspection unchecked
        return (T) field(source, name).get(null);
    }

    public static <T> void setFieldStatic(Class<?> source, String name, T value) throws NoSuchFieldException {
        field(source, name).set(null, value);
    }

    public static <T> T invokeMethod(Object source, String name) throws NoSuchMethodException {
        return invokeMethod(source, name, new Class[0], new Object[0]);
    }

    public static <T> T invokeMethod(Object source, String name, Object... arguments) throws NoSuchMethodException {
        Class<?>[] parameters = parameters(arguments);
        return invokeMethod(source, name, parameters, arguments);
    }

    public static <T> T invokeMethod(Object source, String name, Class<?>[] parameters, Object... arguments) throws NoSuchMethodException {
        //noinspection unchecked,rawtypes
        return (T) ((MethodAccess) method(source.getClass(), name, parameters)).invoke(source, arguments);
    }

    public static <T> T invokeStatic(Class<?> source, String name) throws NoSuchMethodException {
        return invokeStatic(source, name, new Class[0], new Object[0]);
    }

    public static <T> T invokeStatic(Class<?> source, String name, Object... arguments) throws NoSuchMethodException {
        Class<?>[] parameters = parameters(arguments);
        return invokeStatic(source, name, parameters, arguments);
    }

    public static <T> T invokeStatic(Class<?> source, String name, Class<?>[] parameters, Object... arguments) throws NoSuchMethodException {
        // noinspection unchecked
        return (T) method(source, name, parameters).invoke(null, arguments);
    }

    private static Class<?>[] parameters(Object... arguments) {
        return Arrays.stream(arguments)
                .peek(arg -> {
                    if (arg == null)
                        throw new IllegalArgumentException("To invoke a method with null arguments you must specify the parameter types." +
                                " Call #invokeMethod(Object, String, Class[], Object[]) instead.");
                })
                .map(Object::getClass)
                .toArray(Class[]::new);
    }

    public static <S> S invokeConstructor(Class<S> source) throws NoSuchMethodException {
        return invokeConstructor(source, new Class[0], new Object[0]);
    }

    public static <S> S invokeConstructor(Class<S> source, Object... arguments) throws NoSuchMethodException {
        Class<?>[] parameters = Arrays.stream(arguments)
                .peek(arg -> {
                    if (arg == null)
                        throw new IllegalArgumentException("To invoke a constructor with null arguments you must specify the parameter types." +
                                " Call #invokeConstructor(Object, Class[], Object[]) instead.");
                })
                .map(Object::getClass)
                .toArray(Class[]::new);
        return invokeConstructor(source, parameters, arguments);
    }

    public static <S> S invokeConstructor(Class<S> source, Class<?>[] parameters, Object... arguments) throws NoSuchMethodException {
        //noinspection unchecked
        return (S) method(source, "<init>", parameters).invoke(null, arguments);
    }

    public static <S, T> FieldAccess<S, T> field(Class<S> source, String name) throws NoSuchFieldException {
        return fieldAccess(new FieldAccessKey<>(source, name));
    }

    private static <S, T> FieldAccess<S, T> fieldAccess(FieldAccessKey<S> key) throws NoSuchFieldException {
        //noinspection unchecked
        FieldAccess<S, T> fieldAccess = (FieldAccess<S, T>) fieldCache.get(key);
        if (fieldAccess != null)
            return fieldAccess;

        synchronized (fieldCache) {
            //noinspection unchecked
            fieldAccess = (FieldAccess<S, T>) fieldCache.get(key);
            if (fieldAccess != null)
                return fieldAccess;

            Class<?> generated = defineHiddenIn(key.source(), generateFieldAccess(key));
            try {
                //noinspection unchecked
                fieldAccess = (FieldAccess<S, T>) generated.getDeclaredConstructor().newInstance();
                fieldCache.put(key, fieldAccess);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e); // Should not happen
            }
        }

        return fieldAccess;
    }

    public static <S, T> MethodAccess<S, T> method(Class<S> source, String name, Class<?>... parameters) throws NoSuchMethodException {
        return methodAccess(new MethodAccessKey<>(source, name, parameters));
    }

    private static <S, T> MethodAccess<S, T> methodAccess(MethodAccessKey<S> key) throws NoSuchMethodException {
        //noinspection unchecked
        MethodAccess<S, T> methodAccess = (MethodAccess<S, T>) methodCache.get(key);
        if (methodAccess != null)
            return methodAccess;

        synchronized (methodCache) {
            //noinspection unchecked
            methodAccess = (MethodAccess<S, T>) methodCache.get(key);
            if (methodAccess != null)
                return methodAccess;

            Class<?> generated = defineHiddenIn(key.source(), generateMethodAccess(key));
            try {
                //noinspection unchecked
                methodAccess = (MethodAccess<S, T>) generated.getDeclaredConstructor().newInstance();
                methodCache.put(key, methodAccess);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e); // Should not happen
            }
        }

        return methodAccess;
    }

    private static Class<?> defineHiddenIn(Class<?> host, byte[] bytes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(host, MethodHandles.lookup())
                    .defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            return lookup.lookupClass();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e); // Should not happen
        }
    }

    private static byte[] generateFieldAccess(FieldAccessKey<?> key) throws NoSuchFieldException {
        Type objectT = Type.getType(Object.class);
        Type fieldAccessT = Type.getType(FieldAccess.class);
        Type sourceT = Type.getType(key.source());

        Field field = key.field();
        Type fieldT = Type.getType(field.getType());
        Class<?> boxedField = TypeUtils.box(field.getType());
        Type boxedFieldT = boxedField != null ? Type.getType(boxedField) : fieldT;
        boolean isStatic = Modifier.isStatic(field.getModifiers());

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(
                Opcodes.V25,
                Opcodes.ACC_PUBLIC,
                sourceT.getInternalName() + "$Field$" + key.hashCode(),
                accessSignature(fieldAccessT, sourceT, boxedFieldT),
                Type.getInternalName(Object.class),
                new String[] {fieldAccessT.getInternalName()}
        );

        defineConstructor(cw);

        String getterDesc = Type.getMethodDescriptor(objectT, objectT);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", getterDesc, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "get", getterDesc);

        ga.visitCode();
        if (isStatic) {
            ga.getStatic(sourceT, key.name(), fieldT);
        } else {
            ga.loadArg(0);
            ga.checkCast(sourceT);
            ga.getField(sourceT, key.name(), fieldT);
        }
        ga.box(fieldT);
        ga.returnValue();
        ga.endMethod();

        String setterDesc = Type.getMethodDescriptor(Type.getType(void.class), objectT, objectT);
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "set", setterDesc, null, null);
        ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "set", setterDesc);

        ga.visitCode();
        if (Modifier.isFinal(field.getModifiers())) {
            ga.throwException(Type.getType(IllegalAccessException.class), "Cannot modify final field " + field);
        } else {
            if (isStatic) {
                ga.loadArg(1);
                ga.unbox(fieldT);
                ga.putStatic(sourceT, key.name(), fieldT);
            } else {
                ga.loadArg(0);
                ga.checkCast(sourceT);
                ga.loadArg(1);
                ga.unbox(fieldT);
                ga.putField(sourceT, key.name(), fieldT);
            }
            ga.returnValue();
        }
        ga.endMethod();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateMethodAccess(MethodAccessKey<?> key) throws NoSuchMethodException {
        Type objectT = Type.getType(Object.class);
        Type methodAccessT = Type.getType(MethodAccess.class);
        Type sourceT = Type.getType(key.source());

        Executable executable = key.executable();

        Class<?> returnType = key.constructor() ? key.source() : ((java.lang.reflect.Method) executable).getReturnType();
        Type returnTypeT = key.constructor() ? sourceT : Type.getType(returnType);
        Class<?> boxedReturnType = TypeUtils.box(returnType);
        Type boxedReturnTypeT = boxedReturnType != null ? Type.getType(boxedReturnType) : returnTypeT;
        Type[] parametersT = Arrays.stream(key.parameters()).map(Type::getType).toArray(Type[]::new);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(
                Opcodes.V25,
                Opcodes.ACC_PUBLIC,
                sourceT.getInternalName() + "$Method$" + key.hashCode(),
                accessSignature(methodAccessT, sourceT, boxedReturnTypeT),
                Type.getInternalName(Object.class),
                new String[] {methodAccessT.getInternalName()}
        );

        defineConstructor(cw);

        String invokeDesk = Type.getMethodDescriptor(objectT, objectT, Type.getType(Object[].class));
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_VARARGS, "invoke", invokeDesk, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "invoke", invokeDesk);

        ga.visitCode();
        if (key.constructor()) {
            ga.newInstance(sourceT);
            ga.dup();
            loadArgs(ga, key.parameters());
            ga.invokeConstructor(sourceT, new Method(key.name(), Type.VOID_TYPE, parametersT));
        } else if (Modifier.isStatic(executable.getModifiers())) {
            loadArgs(ga, key.parameters());
            ga.invokeStatic(sourceT, new Method(key.name(), returnTypeT, parametersT));

            if (returnTypeT.equals(Type.VOID_TYPE)) {
                ga.push((String) null);
            } else if (isPrimitive(returnTypeT)) {
                ga.box(returnTypeT);
            }
        } else {
            ga.loadArg(0);
            ga.checkCast(sourceT);
            loadArgs(ga, key.parameters());
            ga.invokeVirtual(sourceT, new Method(key.name(), returnTypeT, parametersT));

            if (returnTypeT.equals(Type.VOID_TYPE)) {
                ga.push((String) null);
            } else if (isPrimitive(returnTypeT)) {
                ga.box(returnTypeT);
            }
        }
        ga.returnValue();
        ga.endMethod();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean isPrimitive(Type type) {
        return type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY;
    }

    private static void loadArgs(GeneratorAdapter ga, Class<?>[] parameters) {
        for (int i = 0; i < parameters.length; i++) {
            Type paramT = Type.getType(parameters[i]);
            ga.loadArg(1);
            ga.push(i);
            ga.arrayLoad(Type.getType(Object.class));
            if (!isPrimitive(paramT)) {
                ga.checkCast(paramT);
            } else {
                ga.unbox(paramT);
            }
        }
    }

    private static void defineConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);

        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String accessSignature(Type interfaceType, Type source, Type type) {
        SignatureWriter signature = new SignatureWriter();

        signature.visitSuperclass();
        signature.visitClassType(Type.getInternalName(Object.class));
        signature.visitEnd();

        signature.visitInterface();
        signature.visitClassType(interfaceType.getInternalName());
        signature.visitTypeArgument(SignatureWriter.INSTANCEOF).visitClassType(source.getInternalName());
        signature.visitEnd();

        signature.visitTypeArgument(SignatureWriter.INSTANCEOF).visitClassType(type.getInternalName());
        signature.visitEnd();

        signature.visitEnd();
        return signature.toString();
    }

    public interface FieldAccess<S, T> {
        T get(S source);
        void set(S source, T value);
    }

    @FunctionalInterface
    public interface MethodAccess<S, T> {
        T invoke(S source, Object... args);
    }

    private record FieldAccessKey<S>(Class<? extends S> source, String name) {

        public Field field() throws NoSuchFieldException {
            return source.getDeclaredField(name);
        }

    }

    private record MethodAccessKey<S>(Class<? extends S> source, String name, Class<?>[] parameters) {

        public boolean constructor() {
            return name.equals("<init>");
        }

        public Executable executable() throws NoSuchMethodException {
            return constructor() ? source.getDeclaredConstructor(parameters) : source.getDeclaredMethod(name, parameters);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodAccessKey<?>(Class<?> source1, String name1, Class<?>[] parameters1)))
                return false;

            return name.equals(name1) && Arrays.equals(parameters, parameters1) && source.equals(source1);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(new Object[]{source, name, parameters});
        }

    }

}
