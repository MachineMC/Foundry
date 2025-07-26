package org.machinemc.foundry.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

@DisplayName("UnsafeAllocator Tests")
class UnsafeAllocatorTest {

    private static UnsafeAllocator allocator;

    static class PublicConstructorClass {
        public static final AtomicBoolean CONSTRUCTOR_CALLED = new AtomicBoolean(false);
        String message; // null

        public PublicConstructorClass() {
            CONSTRUCTOR_CALLED.set(true);
            message = "constructor called";
        }
    }

    static class PrivateConstructorClass {
        public static final AtomicBoolean CONSTRUCTOR_CALLED = new AtomicBoolean(false);
        int value; // 0

        private PrivateConstructorClass() {
            CONSTRUCTOR_CALLED.set(true);
            value = 100;
        }
    }

    abstract static class AbstractClass {
        public AbstractClass() {
            throw new AssertionError("AbstractClass constructor called");
        }
    }

    interface InterfaceClass {
    }

    @BeforeAll
    static void setup() {
        allocator = UnsafeAllocator.get();
        assertNotNull(allocator, "UnsafeAllocator instance should not be null");
    }

    @Test
    void testGetInstance() {
        assertNotNull(UnsafeAllocator.get());
    }

    @Test
    void testNewInstance_PublicConstructor() throws Throwable {
        PublicConstructorClass.CONSTRUCTOR_CALLED.set(false);
        PublicConstructorClass instance = allocator.newInstance(PublicConstructorClass.class);

        assertNotNull(instance, "Instance should not be null");
        assertInstanceOf(PublicConstructorClass.class, instance, "Instance should be of PublicConstructorClass type");
        assertFalse(PublicConstructorClass.CONSTRUCTOR_CALLED.get(), "Public constructor should NOT have been called");
        assertNull(instance.message, "Fields should retain default values, not constructor-initialized ones");
    }

    @Test
    void testNewInstance_PrivateConstructor() throws Throwable {
        PrivateConstructorClass.CONSTRUCTOR_CALLED.set(false);
        PrivateConstructorClass instance = allocator.newInstance(PrivateConstructorClass.class);

        assertNotNull(instance, "Instance should not be null");
        assertInstanceOf(PrivateConstructorClass.class, instance, "Instance should be of PrivateConstructorClass type");
        assertFalse(PrivateConstructorClass.CONSTRUCTOR_CALLED.get(), "Private constructor should NOT have been called");
        assertEquals(0, instance.value, "Fields should retain default values, not constructor-initialized ones");
    }

    @Test
    void testNewInstance_AbstractClass() {
        assertThrows(Throwable.class, () -> allocator.newInstance(AbstractClass.class),
                "Should throw an exception for abstract class");
    }

    @Test
    void testNewInstance_Interface() {
        assertThrows(Throwable.class, () -> allocator.newInstance(InterfaceClass.class),
                "Should throw an exception for interface");
    }

}
