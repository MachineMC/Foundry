package org.machinemc.foundry.model;

import org.jetbrains.annotations.Nullable;
import org.machinemc.foundry.Omit;

import java.util.*;

/**
 * Represents a schema of a Java class, defining how to construct instances
 * and access its attributes.
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
     * If they do not follow the common naming scheme, the correct methods to use can be
     * specified with {@link FieldAccess}.
     * <p>
     * Class member are allowed to have {@code private} access modifier.
     * <p>
     * This model enforces two strategies based on the class type:
     * <ul>
     * <li><b>Records:</b> Constructed using the canonical all args constructor. Attributes
     * are accessed with record component accessors.</li>
     *
     * <li><b>Regular Classes:</b> Constructed using the no args constructor and field setters.
     * All fields that are not {@code transient}, {@code static}, or annotated with {@link Omit} must be mutable.
     * </ul>
     *
     * @param type type to generate the model for
     * @param customConstructor optional supplier of the class instances, can only be used with regular classes,
     *                          not records
     * @return model for given type
     */
    public static <T> ClassModel of(Class<T> type, @Nullable CustomConstructor<T> customConstructor) {
        return ClassModelFactory.mapAuto(type, customConstructor);
    }

    /**
     * @see #of(Class, CustomConstructor)
     */
    public static ClassModel of(Class<?> type) {
        return of(type, null);
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
     * The object is created by the all args constructor.
     * <p>
     * This method is used by records.
     */
    public record RecordConstructor() implements ConstructionMethod {
        public static final RecordConstructor INSTANCE = new RecordConstructor();
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
