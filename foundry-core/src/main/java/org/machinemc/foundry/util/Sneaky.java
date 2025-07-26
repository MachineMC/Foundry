package org.machinemc.foundry.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A utility class for "sneakily" throwing checked exceptions without declaring them in a method's signature.
 * This is useful for working with functional interfaces like Runnable or in streams where the lambda body
 * might throw a checked exception that the interface does not support.
 * <p>
 * Warning: This can lead to unexpected behavior if the calling code is not prepared to handle these
 * undeclared exceptions. Use with caution.
 */
public final class Sneaky {

    private Sneaky() {
        throw new UnsupportedOperationException();
    }

    /**
     * Throws a {@link Throwable} without forcing the calling method to declare it.
     *
     * @param throwable the throwable to be thrown
     * @return this method never returns normally
     * @param <T> throwable type
     * @throws T throwable
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    /**
     * A functional interface similar to {@link Function}, but whose {@code apply} method
     * can throw any {@link Throwable}.
     *
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     */
    @FunctionalInterface
    public interface SneakyFunction<T, R> {
        R apply(T t) throws Throwable;
    }

    /**
     * Wraps a {@link SneakyFunction} into a standard {@link Function}, allowing it to be used
     * where a regular Function is expected.
     *
     * @param function the {@link SneakyFunction} to wrap
     * @return a standard {@link Function}
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     */
    public static <T, R> Function<T, R> function(SneakyFunction<T, R> function) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Throwable throwable) {
                throw sneakyThrow(throwable);
            }
        };
    }

    /**
     * A functional interface similar to {@link Runnable}, but whose {@code run} method
     * can throw any {@link Throwable}.
     */
    @FunctionalInterface
    public interface SneakyRunnable {
        void run() throws Throwable;
    }

    /**
     * Wraps a {@link SneakyRunnable} into a standard {@link Runnable}.
     * Any checked exceptions will be sneakily re-thrown.
     *
     * @param runnable the {@link SneakyRunnable} to wrap
     * @return a standard {@link Runnable}
     */
    public static Runnable runnable(SneakyRunnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                throw sneakyThrow(throwable);
            }
        };
    }

    /**
     * A functional interface similar to {@link Supplier}, but whose {@code get} method
     * can throw any {@link Throwable}.
     *
     * @param <T> the type of results supplied by this supplier
     */
    @FunctionalInterface
    public interface SneakySupplier<T> {
        T get() throws Throwable;
    }

    /**
     * Wraps a {@link SneakySupplier} into a standard {@link Supplier}.
     * Any checked exceptions will be sneakily re-thrown.
     *
     * @param supplier the {@link SneakySupplier} to wrap
     * @return a standard {@link Supplier}
     * @param <T> the type of results supplied by the supplier
     */
    public static <T> Supplier<T> supplier(SneakySupplier<T> supplier) {
        return () -> {
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                throw sneakyThrow(throwable);
            }
        };
    }

    /**
     * A functional interface similar to {@link Consumer}, but whose {@code accept} method
     * can throw any {@link Throwable}.
     *
     * @param <T> the type of the input to the operation
     */
    @FunctionalInterface
    public interface SneakyConsumer<T> {
        void accept(T t) throws Throwable;
    }

    /**
     * Wraps a {@link SneakyConsumer} into a standard {@link Consumer}.
     * Any checked exceptions will be sneakily re-thrown.
     *
     * @param consumer the {@link SneakyConsumer} to wrap
     * @return a standard {@link Consumer}
     * @param <T> the type of the input to the operation
     */
    public static <T> Consumer<T> consumer(SneakyConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Throwable throwable) {
                throw sneakyThrow(throwable);
            }
        };
    }

}
