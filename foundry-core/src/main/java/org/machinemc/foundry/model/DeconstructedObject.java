package org.machinemc.foundry.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.AnnotatedType;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * Represents an object that has been deconstructed.
 * It effectively flattens an object into a sequence of {@link Field}s.
 */
public final class DeconstructedObject implements Iterable<DeconstructedObject.Field> {

    /**
     * Creates a function that deconstructs an instance of the specified type into a
     * {@link DeconstructedObject}.
     * <p>
     * The returned function is thread-safe and optimized for repeated use.
     *
     * @param type type the object to deconstruct
     * @param <T> type of the object
     * @return a function that converts an instance of {@link T} into a deconstructed object
     */
    public static <T> Function<T, DeconstructedObject> createDeconstructor(Class<T> type) {
        ClassModel classModel = ClassModel.of(type);
        ObjectFactory<T> objectFactory = ObjectFactory.create(type, classModel);
        FieldsExtractor fieldsExtractor = FieldsExtractor.of(classModel);
        return obj -> {
            ModelDataContainer container = objectFactory.write(obj);
            var fields = fieldsExtractor.read(container);
            return new DeconstructedObject(fields);
        };
    }

    /**
     * Creates a function that reconstructs an instance of the specified type from a
     * {@link DeconstructedObject}.
     * <p>
     * The returned function expects the input {@link DeconstructedObject} to contain fields
     * compatible with the target class schema.
     * <p>
     * The returned function is thread-safe and optimized for repeated use.
     *
     * @param type type the object to reconstruct
     * @param <T> type of the object
     * @return a function that converts deconstructed object into an instance of {@link T}
     */
    public static <T> Function<DeconstructedObject, T> createConstructor(Class<T> type) {
        ClassModel classModel = ClassModel.of(type);
        ObjectFactory<T> objectFactory = ObjectFactory.create(type, classModel);
        FieldsInjector fieldsInjector = FieldsInjector.of(classModel);
        return deconstructed -> {
            ModelDataContainer container = objectFactory.newContainer();
            fieldsInjector.write(deconstructed.fields, container);
            return objectFactory.read(container);
        };
    }

    private final List<Field> fields;

    DeconstructedObject(List<Field> fields) {
        this.fields = fields;
    }

    /**
     * Returns an unmodifiable list of the fields in this deconstructed object.
     *
     * @return the list of fields
     */
    public @Unmodifiable List<Field> asList() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * @return number of fields in this object
     */
    public int size() {
        return fields.size();
    }

    @Override
    public @NotNull Iterator<Field> iterator() {
        return fields.iterator();
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

    public record BoolField(String name, AnnotatedType annotatedType, boolean value) implements Field {
        @Override
        public Class<?> type() {
            return boolean.class;
        }
    }

    public record CharField(String name, AnnotatedType annotatedType, char value) implements Field {
        @Override
        public Class<?> type() {
            return char.class;
        }
    }

    public record ByteField(String name, AnnotatedType annotatedType, byte value) implements Field {
        @Override
        public Class<?> type() {
            return byte.class;
        }
    }

    public record ShortField(String name, AnnotatedType annotatedType, short value) implements Field {
        @Override
        public Class<?> type() {
            return short.class;
        }
    }

    public record IntField(String name, AnnotatedType annotatedType, int value) implements Field {
        @Override
        public Class<?> type() {
            return int.class;
        }
    }

    public record LongField(String name, AnnotatedType annotatedType, long value) implements Field {
        @Override
        public Class<?> type() {
            return long.class;
        }
    }

    public record FloatField(String name, AnnotatedType annotatedType, float value) implements Field {
        @Override
        public Class<?> type() {
            return float.class;
        }
    }

    public record DoubleField(String name, AnnotatedType annotatedType, double value) implements Field {
        @Override
        public Class<?> type() {
            return double.class;
        }
    }

    public record ObjectField(String name, Class<?> type, AnnotatedType annotatedType, Object value) implements Field {
    }

}
