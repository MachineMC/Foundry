package org.machinemc.foundry.util;

import com.google.common.base.Preconditions;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Utility for creating object instances unsafely, bypassing constructors.
 */
public abstract class UnsafeAllocator {

    private static final UnsafeAllocator INSTANCE = create();

    /**
     * Returns the instance of the {@link UnsafeAllocator}.
     *
     * @return the {@link UnsafeAllocator} instance
     */
    public static UnsafeAllocator get() {
        return INSTANCE;
    }

    private UnsafeAllocator() {
    }

    /**
     * Creates a new instance of the specified class without invoking any of its constructors.
     * The fields of the returned object will be in their default initial state
     * (e.g., {@code null} for object references, {@code 0} for numeric primitives,
     * {@code false} for booleans).
     *
     * @param type the type of instance to create.
     * @return a new instance of the specified type
     * @param <T> the type of the instance to create
     * @throws Throwable if an error occurs during instance allocation or if the provided type is abstract
     */
    public abstract <T> T newInstance(Class<T> type) throws Throwable;

    private static Object getUnsafe() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return unsafeField.get(null);
    }

    private static MethodHandle getAllocateInstanceHandle() throws Exception {
        return MethodHandles.lookup()
                .findVirtual(
                        Class.forName("sun.misc.Unsafe"),
                        "allocateInstance",
                        MethodType.methodType(Object.class, Class.class)
                )
                .bindTo(getUnsafe());
    }

    private static UnsafeAllocator create() {
        try {
            return new UnsafeAllocator() {

                private static final MethodHandle HANDLE = getAllocateInstanceHandle(); // full optimization by the JVM

                @Override
                public <T> T newInstance(Class<T> type) throws Throwable {
                    Preconditions.checkState(
                            !Modifier.isAbstract(type.getModifiers())
                                    && !Modifier.isInterface(type.getModifiers()),
                            "Can not create instance of an abstract class"
                    );
                    return type.cast(HANDLE.invokeExact(type));
                }
            };
        } catch (Exception ignored) {
            return new UnsafeAllocator() {
                @Override
                public <T> T newInstance(Class<T> type) {
                    throw new UnsupportedOperationException("Failed to allocate new instance for " + type.getName());
                }
            };
        }
    }

}
