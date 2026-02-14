package org.machinemc.foundry.util;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.signature.SignatureWriter;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return fieldAccess(FieldAccessKey.of(source, name));
    }

    private static <S, T> FieldAccess<S, T> fieldAccess(FieldAccessKey<S> key) {
        //noinspection unchecked
        return (FieldAccess<S, T>) fieldCache.computeIfAbsent(key, k -> {
            Class<?> aClass = defineHiddenIn(key.source(), generateFieldAccess(key));
            try {
                return (FieldAccess<?, ?>) aClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e); // Should not happen
            }
        });
    }

    public static <S, T> MethodAccess<S, T> method(Class<S> source, String name, Class<?>... parameters) throws NoSuchMethodException {
        return methodAccess(MethodAccessKey.of(source, name, parameters));
    }

    private static <S, T> MethodAccess<S, T> methodAccess(MethodAccessKey<S> key) {
        //noinspection unchecked
        return (MethodAccess<S, T>) methodCache.computeIfAbsent(key, k -> {
            Class<?> aClass = defineHiddenIn(key.source(), generateMethodAccess(key));
            try {
                return (MethodAccess<?, ?>) aClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e); // Should not happen
            }
        });
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

    private static byte[] generateFieldAccess(FieldAccessKey<?> key) {
        Type objectT = Type.getType(Object.class);
        Type fieldAccessT = Type.getType(FieldAccess.class);
        Type sourceT = Type.getType(key.source());
        Type fieldT = Type.getType(key.type());
        Class<?> boxedField = TypeUtils.box(key.type());
        Type boxedFieldT = boxedField != null ? Type.getType(boxedField) : fieldT;

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
        if (!key.isStatic()) {
            ga.loadArg(0);
            ga.checkCast(sourceT);
            ga.getField(sourceT, key.name(), fieldT);
        } else {
            ga.getStatic(sourceT, key.name(), fieldT);
        }
        ga.box(fieldT);
        ga.returnValue();
        ga.endMethod();

        String setterDesc = Type.getMethodDescriptor(Type.getType(void.class), objectT, objectT);
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "set", setterDesc, null, null);
        ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, "set", setterDesc);

        ga.visitCode();
        if (key.constant()) {
            ga.throwException(Type.getType(IllegalAccessException.class), "Cannot modify final field " + key.readable());
        } else {
            if (!key.isStatic()) {
                ga.loadArg(0);
                ga.checkCast(sourceT);
                ga.loadArg(1);
                ga.unbox(fieldT);
                ga.putField(sourceT, key.name(), fieldT);
            } else {
                ga.loadArg(1);
                ga.unbox(fieldT);
                ga.putStatic(sourceT, key.name(), fieldT);
            }
            ga.returnValue();
        }
        ga.endMethod();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static byte[] generateMethodAccess(MethodAccessKey<?> key) {
        Type objectT = Type.getType(Object.class);
        Type methodAccessT = Type.getType(MethodAccess.class);
        Type sourceT = Type.getType(key.source());
        Type returnTypeT = Type.getType(key.returnType());
        Class<?> boxedReturnType = TypeUtils.box(key.returnType());
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
        } else if (key.isStatic()) {
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

    private record FieldAccessKey<S>(Class<? extends S> source, String name, Class<?> type, boolean constant, boolean isStatic) {

        public String readable() {
            return (isStatic ? "static " : "") + type.getName() + " " + source.getName() + "." + name;
        }

        public static <S> FieldAccessKey<S> of(Class<? extends S> source, String name) throws NoSuchFieldException {
            Field field = source.getDeclaredField(name);
            int modifiers = field.getModifiers();
            return new FieldAccessKey<>(source, name, field.getType(), (modifiers & Modifier.FINAL) != 0, (modifiers & Modifier.STATIC) != 0);
        }

    }

    private record MethodAccessKey<S>(Class<? extends S> source, String name, Class<?> returnType, Class<?>[] parameters, boolean isStatic) {

        public static <S> MethodAccessKey<S> of(Class<? extends S> source, String name, Class<?>[] parameters) throws NoSuchMethodException {
            if ("<init>".equals(name)) {
                Constructor<?> constructor = source.getDeclaredConstructor(parameters);
                return new MethodAccessKey<>(source, name, source, constructor.getParameterTypes(), false);
            }
            java.lang.reflect.Method method = source.getDeclaredMethod(name, parameters);
            return new MethodAccessKey<>(source, name, method.getReturnType(), method.getParameterTypes(), (method.getModifiers() & Modifier.STATIC) != 0);
        }

        public boolean constructor() {
            return name.equals("<init>");
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodAccessKey<?>(Class<?> source1, String name1, Class<?> type, Class<?>[] parameters1, boolean aStatic)))
                return false;

            return isStatic == aStatic && name.equals(name1) && returnType.equals(type) && Arrays.equals(parameters, parameters1) && source.equals(source1);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(new Object[]{source, name, returnType, parameters, isStatic});
        }

    }

}
