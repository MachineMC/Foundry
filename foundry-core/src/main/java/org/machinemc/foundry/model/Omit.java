package org.machinemc.foundry.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or method to be omitted by {@link ClassModel}.
 * <p>
 * When a field or method is annotated with {@link Omit}, it will be ignored during
 * the creation of a {@link ClassModel}, as if it were not declared in the class at all.
 * This is equivalent to adding a {@code transient} modifier to the field.
 * <p>
 * This is useful for excluding internal caches, or other metadata
 * that should not be considered part of the object's structure.
 * <p>
 * If present on a field, the field and all its possible accessor methods (getters/setters)
 * will be ignored by the class model if created using {@link ClassModel.ModellingStrategy#STRUCTURE}
 * strategy.
 * <p>
 * If present on a method, for {@link ClassModel.ModellingStrategy#STRUCTURE} a direct access to
 * field is still used for both getter or setter access.
 * In case of {@link ClassModel.ModellingStrategy#EXPOSED}, the method is fully ignored. If present on the
 * getter, setter is ignored as well.
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * public class UserProfile {
 *
 *     private String username;
 *     private int score;
 *
 *     // This field will be ignored.
 *     @Omit
 *     private String sessionToken;
 * }}</pre>
 *
 * @see ClassModel
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Omit {
}
