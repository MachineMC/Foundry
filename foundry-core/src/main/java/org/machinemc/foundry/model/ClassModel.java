package org.machinemc.foundry.model;

import org.machinemc.foundry.Omit;

import java.util.*;

/**
 * Represents a schema of a Java class, defining how to construct instances
 * and access its attributes.
 * <p>
 * This model enforces two strategies based on the class type:
 * <ul>
 * <li><b>Records:</b> Constructed using the canonical all args constructor. Attributes
 * are accessed with record component accessors.</li>
 *
 * <li><b>Regular Classes:</b> Constructed using the no args constructor and field setters.
 * All fields that are not transient, static, or annotated with {@link Omit} must be mutable.
 * Foundry automatically finds getter and setter methods for individual fields if they exist
 * and follow regular naming format. If they do not, the methods to use can be
 * specified with {@link FieldAccess}.
 * </ul>
 */
public class ClassModel {

    private final ModelAttribute[] attributes;
    private final ConstructionMethod constructionMethod;

    /**
     * Automatically creates a class model from given type.
     * <p>
     * This works for both regular classes and records.
     * <p>
     * Getters and setters (both non-chaining and chaining) respecting the JavaBeans naming or
     * fluent record naming schemes are prioritized over the direct field access.
     * <p>
     * Class member are allowed to have {@code private} access modifier.
     *
     * @param type type to generate the model for
     * @return model for given type
     */
    public static ClassModel of(Class<?> type) {
        return ClassModelFactory.mapAuto(type);
    }

    protected ClassModel(ModelAttribute[] attributes, ConstructionMethod constructionMethod) {
        this.attributes = attributes;
        this.constructionMethod = constructionMethod;
    }

    /**
     * Returns the attributes of this class model.
     * <p>
     * The order is guaranteed only if the class represented by this model is a record.
     * The attributes are then in returned in the order of record component declaration.
     *
     * @return attributes of this model
     */
    public ModelAttribute[] getAttributes() {
        return Arrays.copyOf(attributes, attributes.length);
    }

    /**
     * @return which constructor is used in this model
     */
    public ConstructionMethod getConstructionMethod() {
        return constructionMethod;
    }

    @Override
    public final boolean equals(Object object) {
        if (!(object instanceof ClassModel other))
            return false;
        return Arrays.equals(attributes, other.attributes) && constructionMethod.equals(other.constructionMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(attributes), constructionMethod.hashCode());
    }

    /**
     * Which construction method is used when creating new instance of the class.
     */
    public sealed interface ConstructionMethod {
    }

    /**
     * No args constructor is called during the creation and the fields are
     * populated via setters.
     * <p>
     * This method is used by regular classes.
     */
    public record NoArgsConstructor() implements ConstructionMethod {
        public static final NoArgsConstructor INSTANCE = new NoArgsConstructor();
    }

    /**
     * The object is created just by the all args constructor.
     * <p>
     * This method is used by records.
     */
    public record AllArgsConstructor() implements ConstructionMethod {
        public static final AllArgsConstructor INSTANCE = new AllArgsConstructor();
    }

    /**
     * Constructor called with first {@code count} attributes.
     * <p>
     * The class must have a constructor accepting those first {@code count} parameters
     * in order of their definition in the class model.
     *
     * @param count number of attributes to read and construct the instance from
     */
    public record AttributeAcceptingConstructor(int count) implements ConstructionMethod {
    }

    /**
     * Custom constructor implementation.
     *
     * @param <T> type of the constructed object
     */
    @FunctionalInterface
    public non-sealed interface CustomConstructor<T> extends ConstructionMethod {

        /**
         * @return constructed object instance
         */
        T get();

    }

}
