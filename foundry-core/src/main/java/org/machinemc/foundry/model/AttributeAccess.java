package org.machinemc.foundry.model;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Represents a method to access a {@link ModelAttribute} within a class.
 *
 * @param getter getter for the attribute
 * @param setter setter for the attribute, is {@code null} if it has the final modifier and can not be changed
 */
public record AttributeAccess(Get getter, @Nullable Set setter) {

    /**
     * Represents getter access.
     */
    public sealed interface Get {
    }

    /**
     * Represents setter access.
     */
    public sealed interface Set {
    }

    /**
     * Represents direct access of the field.
     *
     * @param name name of the field
     */
    public record Direct(String name) implements Get, Set {

        /**
         * Resolves and returns the field instance of this access.
         *
         * @param source source class
         * @return field of this access
         */
        public Field reflect(Class<?> source) throws NoSuchFieldException {
            return source.getDeclaredField(name);
        }

    }

    /**
     * Represents access via a method.
     *
     * @param name name of the method
     * @param params method parameters
     */
    public record Method(Class<?> returnType, String name, @Unmodifiable List<Class<?>> params) implements Get, Set {

        public Method {
            params = ImmutableList.copyOf(params);
        }

        /**
         * Resolves and returns the method instance of this access.
         *
         * @param source source class
         * @return method of this access
         */
        public java.lang.reflect.Method reflect(Class<?> source) throws NoSuchMethodException {
            return source.getDeclaredMethod(name, params.toArray(new Class[0]));
        }

    }

    /**
     * Represents a custom, possibly more complex getter.
     *
     * @param <T> type of the object this getter invokes at
     */
    public sealed interface CustomGetter<T> extends Get {

        @FunctionalInterface
        non-sealed interface Bool<T> extends CustomGetter<T> {
            boolean get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Char<T> extends CustomGetter<T> {
            char get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Byte<T> extends CustomGetter<T> {
            byte get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Short<T> extends CustomGetter<T> {
            short get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Int<T> extends CustomGetter<T> {
            int get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Long<T> extends CustomGetter<T> {
            long get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Float<T> extends CustomGetter<T> {
            float get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Double<T> extends CustomGetter<T> {
            double get(T obj);
        }

        @FunctionalInterface
        non-sealed interface Object<T, V> extends CustomGetter<T> {
            V get(T obj);
        }

    }

    /**
     * Represents a custom, possibly more complex setter.
     *
     * @param <T> type of the object this setter invokes at
     */
    public sealed interface CustomSetter<T> extends Set {

        @FunctionalInterface
        non-sealed interface Bool<T> extends CustomSetter<T> {
            void set(T obj, boolean value);
        }

        @FunctionalInterface
        non-sealed interface Char<T> extends CustomSetter<T> {
            void set(T obj, char value);
        }

        @FunctionalInterface
        non-sealed interface Byte<T> extends CustomSetter<T> {
            void set(T obj, byte value);
        }

        @FunctionalInterface
        non-sealed interface Short<T> extends CustomSetter<T> {
            void set(T obj, short value);
        }

        @FunctionalInterface
        non-sealed interface Int<T> extends CustomSetter<T> {
            void set(T obj, int value);
        }

        @FunctionalInterface
        non-sealed interface Long<T> extends CustomSetter<T> {
            void set(T obj, long value);
        }

        @FunctionalInterface
        non-sealed interface Float<T> extends CustomSetter<T> {
            void set(T obj, float value);
        }

        @FunctionalInterface
        non-sealed interface Double<T> extends CustomSetter<T> {
            void set(T obj, double value);
        }

        @FunctionalInterface
        non-sealed interface Object<T, V> extends CustomSetter<T> {
            void set(T obj, V value);
        }

    }

}
