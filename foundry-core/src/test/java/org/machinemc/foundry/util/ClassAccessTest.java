package org.machinemc.foundry.util;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ClassAccessTest {

    static class Base {
        private int basePrivate = 7;
        protected String baseProtected = "base";
        public String basePublic = "pub";

        private String baseSecret() { return "secret:" + basePrivate; }
    }

    static class Target extends Base {

        private int i = 10;
        private Integer boxedI = 11;
        private boolean flag = true;
        private long l = 123L;
        private double d = 1.25;
        private String s = "hi";
        private final int finalI = 42;

        private static int si = 5;
        private static final String S_FINAL = "CONST";

        private int add(int a, int b) { return a + b; }
        private Integer boxedAdd(Integer a, Integer b) { return a + b; }
        private void setS(String v) { this.s = v; }
        private String join3(String a, String b, String c) { return a + b + c; }
        private boolean negate(boolean v) { return !v; }
        private long mul(long a, long b) { return a * b; }
        private double div(double a, double b) { return a / b; }
        private int sumVarargs(int... xs) {
            int sum = 0;
            for (int x : xs) sum += x;
            return sum;
        }

        private String overload(Number n) { return "N:" + n; }
        private String overload(Integer n) { return "I:" + n; }

        private static int sadd(int a, int b) { return a + b; }
        private static void sset(int v) { si = v; }
        private static String sjoin(String a, String b) { return a + b; }

        private Target() {
            this.i = 1;
            this.s = "ctor0";
        }
        private Target(int i) {
            this.i = i;
            this.s = "ctor1";
        }
        private Target(int i, String s) {
            this.i = i;
            this.s = s;
        }
    }

    private static Object reflectGetStatic(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getSet_instance_private_fields_primitives_and_refs() throws Exception {
        Target t = new Target(99, "x");

        assertEquals(99, ClassAccess.<Integer>getField(t, "i"));
        ClassAccess.setField(t, "i", 123);
        assertEquals(123, ClassAccess.<Integer>getField(t, "i"));

        assertEquals("x", ClassAccess.<String>getField(t, "s"));
        ClassAccess.setField(t, "s", "y");
        assertEquals("y", ClassAccess.<String>getField(t, "s"));

        assertEquals(true, ClassAccess.<Boolean>getField(t, "flag"));
        ClassAccess.setField(t, "flag", false);
        assertEquals(false, ClassAccess.<Boolean>getField(t, "flag"));

        assertEquals(123L, ClassAccess.<Long>getField(t, "l"));
        ClassAccess.setField(t, "l", 777L);
        assertEquals(777L, ClassAccess.<Long>getField(t, "l"));

        assertEquals(1.25, ClassAccess.<Double>getField(t, "d"));
        ClassAccess.setField(t, "d", 2.5);
        assertEquals(2.5, ClassAccess.<Double>getField(t, "d"));
    }

    @Test
    void getSet_static_private_field() throws Exception {
        assertEquals(5, ClassAccess.<Integer>getFieldStatic(Target.class, "si"));
        ClassAccess.setFieldStatic(Target.class, "si", 99);
        assertEquals(99, ClassAccess.<Integer>getFieldStatic(Target.class, "si"));
    }

    @Test
    void set_final_field_throws() throws Exception {
        Target t = new Target(1, "a");

        assertThrows(IllegalAccessException.class, () -> ClassAccess.setField(t, "finalI", 7));
        assertEquals(42, ClassAccess.<Integer>getField(t, "finalI"));
    }

    @Test
    void set_static_final_field_throws_and_constant_unchanged() throws Exception {
        assertThrows(IllegalAccessException.class, () -> ClassAccess.setFieldStatic(Target.class, "S_FINAL", "NOPE"));
        assertEquals("CONST", reflectGetStatic(Target.class, "S_FINAL"));
    }

    @Test
    void field_missing_throws_NoSuchFieldException() {
        Target t = new Target(1, "a");
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.getField(t, "doesNotExist"));
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.setField(t, "doesNotExist", 1));
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.getFieldStatic(Target.class, "doesNotExist"));
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.setFieldStatic(Target.class, "doesNotExist", 1));
    }

    @Test
    void field_declared_vs_inherited() throws Exception {
        Target t = new Target(1, "a");
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.getField(t, "basePrivate"));
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.getField(t, "basePublic"));
        assertThrows(NoSuchFieldException.class, () -> ClassAccess.getField(t, "baseProtected"));

        assertEquals(7, ClassAccess.<Base, Integer>field(Base.class, "basePrivate").get(t));
    }

    @Test
    void invoke_instance_private_methods_primitives_refs_void() throws Exception {
        Target t = new Target(1, "a");

        assertEquals(7, ClassAccess.<Integer>invokeMethod(t, "add", new Class[]{int.class, int.class}, Integer.valueOf(3), 4));

        assertEquals(9, ClassAccess.<Integer>invokeMethod(t, "boxedAdd", new Class[]{Integer.class, Integer.class},
                Integer.valueOf(4), 5));

        assertNull(ClassAccess.invokeMethod(t, "setS", new Class[]{String.class}, "zzz"));
        assertEquals("zzz", ClassAccess.<String>getField(t, "s"));

        assertEquals("abc", ClassAccess.<String>invokeMethod(t, "join3",
                new Class[]{String.class, String.class, String.class}, "a", "b", "c"));

        assertEquals(false, ClassAccess.<Boolean>invokeMethod(t, "negate", new Class[]{boolean.class}, true));

        assertEquals(12L, ClassAccess.<Long>invokeMethod(t, "mul", new Class[]{long.class, long.class}, 3L, 4L));

        assertEquals(2.0, ClassAccess.<Double>invokeMethod(t, "div", new Class[]{double.class, double.class}, 4.0, 2.0));
    }

    @Test
    void invoke_static_private_methods_primitives_refs_void() throws Exception {
        assertEquals(7, ClassAccess.<Integer>invokeStatic(Target.class, "sadd",
                new Class[]{int.class, int.class}, 3, 4));

        assertNull(ClassAccess.invokeStatic(Target.class, "sset", new Class[]{int.class}, 123));
        assertEquals(123, ClassAccess.<Integer>getFieldStatic(Target.class, "si"));

        assertEquals("ab", ClassAccess.<String>invokeStatic(Target.class, "sjoin",
                new Class[]{String.class, String.class}, "a", "b"));
    }

    @Test
    void invoke_overload_resolution_requires_parameters_when_null() throws Exception {
        Target t = new Target(1, "a");

        assertEquals("I:null", ClassAccess.<String>invokeMethod(t, "overload",
                new Class[]{Integer.class}, new Object[]{null}));

        assertEquals("N:null", ClassAccess.<String>invokeMethod(t, "overload",
                new Class[]{Number.class}, new Object[]{null}));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ClassAccess.invokeMethod(t, "overload", (Object) null));
        assertTrue(ex.getMessage().contains("null arguments"));
    }

    @Test
    void invoke_without_parameters_infers_runtime_classes_not_primitives_and_can_fail() throws Exception {
        Target t = new Target(1, "a");

        assertThrows(NoSuchMethodException.class, () -> ClassAccess.invokeMethod(t, "add", 1, 2));

        assertEquals(3, ClassAccess.<Integer>invokeMethod(t, "boxedAdd", Integer.valueOf(1), Integer.valueOf(2)));
    }

    @Test
    void invoke_missing_method_throws_NoSuchMethodException() {
        Target t = new Target(1, "a");
        assertThrows(NoSuchMethodException.class, () -> ClassAccess.invokeMethod(t, "nope"));
        assertThrows(NoSuchMethodException.class, () -> ClassAccess.invokeStatic(Target.class, "nope"));
        assertThrows(NoSuchMethodException.class, () -> ClassAccess.invokeConstructor(Target.class, String.class));
    }

    @Test
    void invoke_wrong_arg_count_causes_ArrayIndexOutOfBounds() throws Exception {
        Target t = new Target(1, "a");
        assertThrows(ArrayIndexOutOfBoundsException.class, () ->
                ClassAccess.invokeMethod(t, "add", new Class[]{int.class, int.class}, 1));
    }

    @Test
    void invoke_wrong_arg_type_causes_ClassCastException_or_unboxing_NPE() throws Exception {
        Target t = new Target(1, "a");

        assertThrows(ClassCastException.class, () ->
                ClassAccess.invokeMethod(t, "setS", new Class[]{String.class}, 123));

        assertThrows(NullPointerException.class, () ->
                ClassAccess.invokeMethod(t, "add", new Class[]{int.class, int.class}, null, 1));
    }

    @Test
    void invoke_varargs_method_requires_explicit_array_type() throws Exception {
        Target t = new Target(1, "a");

        assertEquals(6, ClassAccess.<Integer>invokeMethod(t, "sumVarargs", new Class[]{int[].class}, new int[]{1,2,3}));

        assertThrows(NoSuchMethodException.class, () -> ClassAccess.invokeMethod(t, "sumVarargs", 1,2,3));
    }

    @Test
    void invoke_private_constructors() throws Exception {
        Target a = ClassAccess.invokeConstructor(Target.class);
        assertEquals("ctor0", ClassAccess.<String>getField(a, "s"));
        assertEquals(1, ClassAccess.<Integer>getField(a, "i"));

        Target b = ClassAccess.invokeConstructor(Target.class, new Class[]{int.class}, 77);
        assertEquals("ctor1", ClassAccess.<String>getField(b, "s"));
        assertEquals(77, ClassAccess.<Integer>getField(b, "i"));

        Target c = ClassAccess.invokeConstructor(Target.class, new Class[]{int.class, String.class}, 88, "zz");
        assertEquals("zz", ClassAccess.<String>getField(c, "s"));
        assertEquals(88, ClassAccess.<Integer>getField(c, "i"));
    }

    @Test
    void invoke_constructor_null_arg_requires_parameters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ClassAccess.invokeConstructor(Target.class, (Object) null));
        assertTrue(ex.getMessage().contains("null arguments"));
    }

    @Test
    void caches_return_same_accessor_instance_per_key() throws Exception {
        var f1 = ClassAccess.field(Target.class, "i");
        var f2 = ClassAccess.field(Target.class, "i");
        assertSame(f1, f2);

        var m1 = ClassAccess.method(Target.class, "add", int.class, int.class);
        var m2 = ClassAccess.method(Target.class, "add", int.class, int.class);
        assertSame(m1, m2);
    }

    @Test
    void cache_mapper_failure_does_not_poison_cache_for_final_field() throws Exception {
        Target t = new Target(1, "a");

        assertThrows(IllegalAccessException.class, () -> ClassAccess.setField(t, "finalI", 9));
        assertThrows(IllegalAccessException.class, () -> ClassAccess.setField(t, "finalI", 9));
    }

    @Test
    void can_access_private_members() throws Exception {
        Target t = new Target(2, "a");

        assertEquals(5, ClassAccess.<Integer>invokeMethod(t, "add", new Class[]{int.class, int.class}, 2, 3));
        assertEquals(2, ClassAccess.<Integer>getField(t, "i"));
        ClassAccess.setField(t, "i", 200);
        assertEquals(200, ClassAccess.<Integer>getField(t, "i"));
    }

}
