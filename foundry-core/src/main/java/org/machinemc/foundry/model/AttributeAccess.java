package org.machinemc.foundry.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Field;
import java.util.Collections;
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
            params = Collections.unmodifiableList(params);
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

}
