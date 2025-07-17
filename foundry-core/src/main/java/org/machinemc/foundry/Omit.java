package org.machinemc.foundry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to be omitted by {@link org.machinemc.foundry.Template}.
 * <p>
 * When a field is annotated with {@link Omit}, it will be ignored during
 * the creation of a {@code Template}, as if it were not declared in the class at all.
 * This is equivalent to adding a {@code transient} modifier to the field.
 * <p>
 * This is useful for excluding internal caches, or other metadata
 * that should not be considered part of the object's structure.
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * public class UserProfile {
 *
 *     private String username;
 *     private int score;
 *
 *     // This field will be ignored by the Template class.
 *     @Omit
 *     private String sessionToken;
 * }
 *
 * // The resulting template will only contain 'username' and 'score'.
 * Template userProfileTemplate = Template.of(UserProfile.class);
 * }</pre>
 *
 * @see org.machinemc.foundry.Template
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Omit {
}
