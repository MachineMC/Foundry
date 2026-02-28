package org.machinemc.foundry.model;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Represents a schema of a Java class, defining how to construct instances
 * and access its attributes.
 */
public class ClassModel<T> {

    private final Class<T> source;
    private final ModelAttribute[] attributes;
    private final ConstructionMethod constructionMethod;

    /**
     * Automatically creates a class model from the given type by inferring the correct mapping route.
     *
     * @param type the type to generate the model for
     * @return class model for given type
     * @param <T> type
     * @see #of(Class, ModellingStrategy, ConstructionMethod)
     */
    public static <T> ClassModel<T> of(Class<T> type) {
        Preconditions.checkArgument(!type.isInterface(), "Type can not be an interface");
        Preconditions.checkArgument(!Modifier.isAbstract(type.getModifiers()), "Type can not be abstract");
        if (type.isRecord())
            return of(type, ModellingStrategy.STRUCTURE, RecordConstructor.INSTANCE);
        if (type.isEnum())
            return of(type, ModellingStrategy.STRUCTURE, null);
        return of(type, ModellingStrategy.STRUCTURE, NoArgsConstructor.INSTANCE);
    }

    /**
     * Automatically creates a class model from the given type by inferring the correct mapping route.
     * <p>
     * <ul>
     * <li><b>Interfaces:</b> Must use {@link ModellingStrategy#EXPOSED} and provide a {@link CustomConstructor}.</li>
     * <li><b>Records:</b> Must use {@link RecordConstructor}.</li>
     * <li><b>Enums:</b> Must provide an {@link EnumConstructor} or {@code null},
     * then {@link EnumConstructor#valueOf(Class)} is used.</li>
     * <li><b>Standard Classes:</b> Can use {@link NoArgsConstructor} or a {@link CustomConstructor}.</li>
     * </ul>
     *
     * @param type the type to generate the model for
     * @param modellingStrategy the strategy to discover class attributes
     * @param constructionMethod the method used to construct instances of the class
     * @return class model for given type
     * @param <T> type
     */
    public static <T> ClassModel<T> of(Class<T> type, ModellingStrategy modellingStrategy,
                                       @Nullable ConstructionMethod constructionMethod) {
        if (type.isInterface()) {
            if (!(constructionMethod instanceof ClassModel.CustomConstructor<?> custom))
                throw new IllegalArgumentException("Interfaces must provide custom constructor");
            Preconditions.checkArgument(modellingStrategy == ModellingStrategy.EXPOSED,
                    "Interfaces must use EXPOSED modelling strategy");
            //noinspection unchecked
            return ofInterface(type, (ClassModel.CustomConstructor<T>) custom);
        }

        if (type.isRecord()) {
            Preconditions.checkArgument(constructionMethod instanceof RecordConstructor, "Record models "
                    + "must use the record canonical constructors");
            // both modelling strategies are valid and will result in the same model by the specification
            //noinspection unchecked
            return (ClassModel<T>) ofRecord((Class<? extends Record>) type);
        }

        if (type.isEnum()) {
            if (constructionMethod == null)
                //noinspection unchecked,rawtypes
                constructionMethod = EnumConstructor.valueOf((Class) type);
            if (!(constructionMethod instanceof ClassModel.EnumConstructor<?> enumConstructor))
                throw new IllegalArgumentException("Enums must provide enum constructor");
            //noinspection unchecked,rawtypes
            return ofEnum((Class) type, modellingStrategy, enumConstructor);
        }

        CustomConstructor<T> custom;
        if (!(constructionMethod instanceof ClassModel.CustomConstructor<?>)) {
            Preconditions.checkArgument(constructionMethod instanceof NoArgsConstructor,
                    "Provided illegal construction method for standard class '%s'", type.getName());
            custom = null;
        } else {
            //noinspection unchecked
            custom = (CustomConstructor<T>) constructionMethod;
        }

        return ofClass(type, modellingStrategy, custom);
    }

    /**
     * Automatically creates a class model for given standard class.
     *
     * @param type the type to generate the model for
     * @param modellingStrategy the strategy to discover class attributes
     * @param customConstructor custom constructor, can be {@code null} if no argument constructor is present
     * @return class model for given type
     * @param <T> type
     */
    public static <T> ClassModel<T> ofClass(Class<T> type,
                                            ModellingStrategy modellingStrategy,
                                            @Nullable CustomConstructor<T> customConstructor) {
        return ClassModelFactory.mapClass(type, modellingStrategy, customConstructor);
    }

    /**
     * Automatically creates a class model for given interface.
     *
     * @param type the type to generate the model for
     * @param customConstructor custom constructor, must be present
     * @return class model for given type
     * @param <T> type
     */
    public static <T> ClassModel<T> ofInterface(Class<T> type, CustomConstructor<T> customConstructor) {
        return ClassModelFactory.mapInterface(type, customConstructor);
    }

    /**
     * Automatically creates a class model for given record.
     *
     * @param type the type to generate the model for
     * @return class model for given type
     * @param <T> type
     */
    public static <T extends Record> ClassModel<T> ofRecord(Class<T> type) {
        return ClassModelFactory.mapRecord(type);
    }

    /**
     * Automatically creates a class model for given enum.
     *
     * @param type the type to generate the model for
     * @param modellingStrategy the strategy to discover class attributes
     * @param enumConstructor custom constructor, can be {@code null}, then {@link EnumConstructor#valueOf(Class)} is
     *                        used.
     * @return class model for given type
     * @param <T> type
     */
    public static <T extends Enum<T>> ClassModel<T> ofEnum(Class<T> type,
                                                           ModellingStrategy modellingStrategy,
                                                           @Nullable EnumConstructor<T> enumConstructor) {
        return ClassModelFactory.mapEnum(type, modellingStrategy, enumConstructor);
    }

    protected ClassModel(Class<T> source, ModelAttribute[] attributes, ConstructionMethod constructionMethod) {
        this.source = source;
        this.attributes = attributes;
        this.constructionMethod = constructionMethod;
    }

    /**
     * Returns the source class of this class model
     *
     * @return source
     */
    public Class<T> getSource() {
        return source;
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
        if (!(object instanceof ClassModel<?> other))
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
     * Custom constructor implementation where the enum is resolved from the name.
     * <p>
     * This method is used by enums.
     *
     * @param <T> type of the enum
     */
    public non-sealed interface EnumConstructor<T extends Enum<T>> extends ConstructionMethod {

        /**
         * Returns the basic implementation that returns the enum with exact name or
         * throws {@link IllegalArgumentException} if the specified enum class has no
         * constant with the specified name.
         *
         * @param type enum type
         * @return constructor
         * @param <T> enum type
         */
        static <T extends Enum<T>> EnumConstructor<T> valueOf(Class<T> type) {
            return name -> Enum.valueOf(type, name);
        }

        /**
         * @return constructed object instance
         */
        T get(String name);

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

    /**
     * Specifies the process of automatic class model creation.
     */
    public enum ModellingStrategy {

        /**
         * Scans the class (and its parents) for declared fields (including private ones)
         * and maps attributes based on the class structure.
         */
        STRUCTURE,

        /**
         * Scans the class for exposed public accessor methods (getters and setters)
         * and maps attributes based on that.
         */
        EXPOSED

    }

}
