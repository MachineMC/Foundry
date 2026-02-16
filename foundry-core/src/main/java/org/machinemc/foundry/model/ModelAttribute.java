package org.machinemc.foundry.model;

import java.lang.reflect.AnnotatedType;

/**
 * Represents a serializable attribute of a class.
 *
 * @param source source class
 * @param name name of the attribute
 * @param type type of the attribute
 * @param annotatedType annotated type of this attribute if present
 * @param access access for this attribute
 */
public record ModelAttribute(Class<?> source, String name, Class<?> type, AnnotatedType annotatedType,
                             AttributeAccess access) {

    /**
     * @return whether this attribute is primitive
     */
    public boolean primitive() {
        return type.isPrimitive();
    }

}
