package org.machinemc.foundry.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to specify the annotated method is a getter or setter for a field.
 * <p>
 * This annotation can be used if the getter or setter method does not
 * follow the standard format or is named differently than the field itself.
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * public class BadlyNamed {
 *
 *     int foo = 10;
 *
 *     @FieldAccess("foo")
 *     int thisMethodGetsFoo() {
 *         return foo;
 *     }
 * }}</pre>
 *
 * In this example, the {@code thisMethodGetsFoo} will get called to read the value
 * of the {@code int foo} field
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FieldAccess {

    /**
     * @return name of the field this method targets
     */
    String value();

}
