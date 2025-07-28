package org.machinemc.foundry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as optional, suggesting that it can have a {@code null} value
 * assigned to it during serialization or deserialization processes.
 * <p>
 * This is useful for fields that may not always be present in the source data
 * or that are genuinely optional components of an object's structure.
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * public class Product {
 *
 *     private String id;
 *     private String name;
 *
 *     // This field might not always be present in product data.
 *     // If missing during deserialization, it will be set to null.
 *     @AllowNull
 *     private String description;
 * }
 *
 * // The resulting template will only set 'description' as nullable.
 * Template productTemplate = Template.of(Product.class);
 * }</pre>
 *
 * @see org.machinemc.foundry.Template
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AllowNull {
}
