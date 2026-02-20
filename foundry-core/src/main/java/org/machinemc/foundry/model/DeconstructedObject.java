package org.machinemc.foundry.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.machinemc.foundry.Codec;
import org.machinemc.foundry.DataHandler;
import org.machinemc.foundry.Pipeline;

import java.lang.reflect.AnnotatedType;
import java.util.*;

/**
 * Represents an object that has been deconstructed.
 * It effectively flattens an object into a sequence of {@link Field}s.
 */
public final class DeconstructedObject implements Iterable<DeconstructedObject.Field> {

    /**
     * Creates a codec that transforms objects of type {@link T} into
     * {@link DeconstructedObject} and back.
     * <p>
     * The returned codec is thread-safe and optimized for repeated use.
     *
     * @param type type the object to (de)construct
     * @param <T> type of the object
     * @return a codec that converts an instance of {@link T} into a deconstructed object and back
     */
    public static <T> Codec<T, DeconstructedObject> codec(Class<T> type) {
        ClassModel classModel = ClassModel.of(type);
        ObjectFactory<T> objectFactory = ObjectFactory.create(type, classModel);
        return new Codec<>(
                Pipeline.builder(createDeconstructor(classModel, objectFactory)).build(),
                Pipeline.builder(createConstructor(classModel, objectFactory)).build()
        );
    }

    /**
     * Creates a data handler that deconstructs an instance of the specified type into a
     * {@link DeconstructedObject}.
     * <p>
     * The returned data handler is thread-safe and optimized for repeated use.
     *
     * @param type type the object to deconstruct
     * @param <T> type of the object
     * @return a data handler that converts an instance of {@link T} into a deconstructed object
     */
    public static <T> DataHandler<T, DeconstructedObject> createDeconstructor(Class<T> type) {
        ClassModel classModel = ClassModel.of(type);
        return createDeconstructor(classModel, ObjectFactory.create(type, classModel));
    }

    /**
     * Creates a data handler that reconstructs an instance of the specified type from a
     * {@link DeconstructedObject}.
     * <p>
     * The returned data handler expects the input {@link DeconstructedObject} to contain fields
     * compatible with the target class schema.
     * <p>
     * The returned data handler is thread-safe and optimized for repeated use.
     *
     * @param type type the object to reconstruct
     * @param <T> type of the object
     * @return a data handler that converts deconstructed object into an instance of {@link T}
     */
    public static <T> DataHandler<DeconstructedObject, T> createConstructor(Class<T> type) {
        ClassModel classModel = ClassModel.of(type);
        return createConstructor(classModel, ObjectFactory.create(type, classModel));
    }

    // TODO this must also take source class as field names can overlap
    private final @Unmodifiable Map<String, Field> fields;

    DeconstructedObject(Map<String, Field> fields) {
        this.fields = Collections.unmodifiableMap(fields);
    }

    /**
     * Returns field with given name of empty if none exists.
     *
     * @param name name of the field
     * @return field with given name
     */
    public Optional<Field> getField(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    /**
     * Returns an unmodifiable map of the fields in this deconstructed object, mapped
     * by their names.
     *
     * @return map of fields
     */
    public @Unmodifiable Map<String, Field> asMap() {
        return fields;
    }

    /**
     * Returns an unmodifiable list of the fields in this deconstructed object.
     *
     * @return list of fields
     */
    public @Unmodifiable List<Field> asList() {
        return List.copyOf(fields.values());
    }

    /**
     * @return number of fields in this object
     */
    public int size() {
        return fields.size();
    }

    @Override
    public @NotNull Iterator<Field> iterator() {
        return fields.values().iterator();
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DeconstructedObject other))
            return false;
        return fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    /**
     * Represents a field of a deconstructed object.
     */
    public sealed interface Field {

        /**
         * @return name of the field
         */
        String name();

        /**
         * @return type of the field
         */
        Class<?> type();

        /**
         * @return annotated type of the field
         */
        AnnotatedType annotatedType();

    }

    /**
     * Primitive boolean field.
     */
    public record BoolField(String name, AnnotatedType annotatedType, boolean value) implements Field {
        @Override
        public Class<?> type() {
            return boolean.class;
        }
    }

    /**
     * Primitive char field.
     */
    public record CharField(String name, AnnotatedType annotatedType, char value) implements Field {
        @Override
        public Class<?> type() {
            return char.class;
        }
    }

    /**
     * Primitive byte field.
     */
    public record ByteField(String name, AnnotatedType annotatedType, byte value) implements Field {
        @Override
        public Class<?> type() {
            return byte.class;
        }
    }

    /**
     * Primitive short field.
     */
    public record ShortField(String name, AnnotatedType annotatedType, short value) implements Field {
        @Override
        public Class<?> type() {
            return short.class;
        }
    }

    /**
     * Primitive int field.
     */
    public record IntField(String name, AnnotatedType annotatedType, int value) implements Field {
        @Override
        public Class<?> type() {
            return int.class;
        }
    }

    /**
     * Primitive long field.
     */
    public record LongField(String name, AnnotatedType annotatedType, long value) implements Field {
        @Override
        public Class<?> type() {
            return long.class;
        }
    }

    /**
     * Primitive float field.
     */
    public record FloatField(String name, AnnotatedType annotatedType, float value) implements Field {
        @Override
        public Class<?> type() {
            return float.class;
        }
    }

    /**
     * Primitive double field.
     */
    public record DoubleField(String name, AnnotatedType annotatedType, double value) implements Field {
        @Override
        public Class<?> type() {
            return double.class;
        }
    }

    /**
     * Object field.
     */
    public record ObjectField(String name, Class<?> type, AnnotatedType annotatedType, Object value) implements Field {
    }

    private static <T> DataHandler<T, DeconstructedObject> createDeconstructor(ClassModel classModel,
                                                                               ObjectFactory<T> objectFactory) {
        FieldsExtractor fieldsExtractor = FieldsExtractor.of(classModel);
        return obj -> {
            ModelDataContainer container = objectFactory.write(obj);
            var fields = fieldsExtractor.read(container);
            return new DeconstructedObject(fields);
        };
    }

    private static <T> DataHandler<DeconstructedObject, T> createConstructor(ClassModel classModel,
                                                                             ObjectFactory<T> objectFactory) {
        FieldsInjector fieldsInjector = FieldsInjector.of(classModel);
        return deconstructed -> {
            ModelDataContainer container = objectFactory.newContainer();
            fieldsInjector.write(deconstructed.asList(), container);
            return objectFactory.read(container);
        };
    }

}
