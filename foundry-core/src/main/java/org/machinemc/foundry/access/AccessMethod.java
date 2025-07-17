package org.machinemc.foundry.access;

/**
 * Represents a mechanism by which a field or method can be accessed.
 */
public enum AccessMethod {

    /**
     * Access through the Java Reflection API.
     * <p>
     * About 104% slower compare to direct access.
     */
    REFLECTION,

    /**
     * Access using a {@link java.lang.invoke.MethodHandle}.
     * <p>
     * About 136% slower compare to direct access.
     */
    METHOD_HANDLE,

    /**
     * Access using a static {@link java.lang.invoke.MethodHandle}.
     * <p>
     * About 2% slower compare to direct access.
     * <p>
     * Loads a new class per field/method.
     */
    STATIC_METHOD_HANDLE,

    /**
     * Direct method invocation.
     * <p>
     * Loads a new class per field/method.
     */
    DIRECT,

    /**
     * Access via a {@link java.lang.invoke.LambdaMetafactory}.
     * <p>
     * About 33% slower compare to direct access.
     */
    LAMBDA_META_FACTORY

}
