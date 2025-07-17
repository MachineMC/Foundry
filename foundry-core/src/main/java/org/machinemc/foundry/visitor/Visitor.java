package org.machinemc.foundry.visitor;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.util.Token;
import org.machinemc.foundry.util.TypeUtils;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;

/**
 * Interface for implementing the visitor design pattern.
 *
 * @param <O> output data type
 * @see Visit
 * @see VisitorHandler
 */
@FunctionalInterface
public interface Visitor<O> {

    /**
     * Visits the given input object using its class as the type.
     *
     * @param input the input data
     * @param object object to visit
     * @return the output data
     * @param <T> object type
     */
    default <T> O visit(O input, T object) {
        Preconditions.checkNotNull(object, "Object can not be null");
        return visit(input, object, object.getClass());
    }

    /**
     * Visits the given input object with its full generic type captured by a {@link Token} type token.
     * <p>
     * This is the recommended method for visiting objects where their generic type or annotations are
     * important.
     *
     * @param input the input data
     * @param object object to visit
     * @param type a type token that has captured the full generic type
     * @return the output data
     * @param <T> object type
     */
    default <T> O visit(O input, @Nullable T object, Token<T> type) {
        return visit(input, object, type.get());
    }

    /**
     * Performs an operation on an input object given its specific {@link Type}.
     * <p>
     * Implementations of this method should define the logic for type-aware processing
     * of the visited object.
     *
     * @param input the input data
     * @param object object to visit
     * @param type the type of the input object
     * @return the output data
     * @param <T> object type
     */
    default <T> O visit(O input, @Nullable T object, Type type) {
        return visit(input, object, TypeUtils.getAnnotatedType(type));
    }

    /**
     * Performs an operation on an input object given its specific {@link AnnotatedType}.
     * <p>
     * Implementations of this method should define the logic for type-aware processing
     * of the visited object.
     *
     * @param input the input data
     * @param object object to visit
     * @param type the type of the input object
     * @return the output data
     * @param <T> object type
     */
    <T> O visit(O input, @Nullable T object, AnnotatedType type);

}
