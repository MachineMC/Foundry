package org.machinemc.foundry.util;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;

/**
 * A class used to capture and preserve full generic type information at runtime.
 *
 * <p><b>Example Usage:</b></p>
 * <p>To use this class, you create an anonymous inner class that extends it,
 * specifying the desired generic type as the type parameter. This can also include annotations.</p>
 *
 * <pre>{@code
 * // Capture the type List<String>
 * var stringListType = new Token<List<String>>() {};
 *
 * // Capture the type Map<String, Integer>
 * var mapType = new Token<Map<String, Integer>>() {};
 *
 * }</pre>
 *
 * @param <__> The generic type to be captured
 */
public abstract class Token<__> {

    /**
     * Returns the captured full type of this token.
     *
     * @return the captured type
     */
    public AnnotatedType get() {
        AnnotatedType superType = getClass().getAnnotatedSuperclass();
        if (!(superType instanceof AnnotatedParameterizedType apt))
            throw new IllegalArgumentException("The token has not parameter");
        return apt.getAnnotatedActualTypeArguments()[0];
    }

    protected Token() {
    }

}
