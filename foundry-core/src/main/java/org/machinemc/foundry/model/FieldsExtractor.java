package org.machinemc.foundry.model;

import java.lang.reflect.AnnotatedType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Optimized reader of {@link ModelDataContainer} to map them to deconstructed objects.
 * <p>
 * This class is for internal use only.
 *
 * @param fieldReaders field readers
 */
record FieldsExtractor(Function<ModelDataContainer, DeconstructedObject.Field>[] fieldReaders) {

    /**
     * Creates new fields extractor instance for given class model.
     *
     * @param model model
     * @return fields extractor for given model
     */
    static FieldsExtractor of(ClassModel model) {
        ModelAttribute[] attributes = model.getAttributes();
        //noinspection unchecked
        Function<ModelDataContainer, DeconstructedObject.Field>[] fieldReaders = new Function[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            ModelAttribute attribute = attributes[i];
            String name = attribute.name();
            AnnotatedType annotatedType = attribute.annotatedType();
            Function<ModelDataContainer, DeconstructedObject.Field> reader;
            if (attribute.type() == boolean.class) {
                reader = dataContainer ->
                        new DeconstructedObject.BoolField(name, annotatedType, dataContainer.readBool());
            } else if (attribute.type() == char.class) {
                reader = dataContainer ->
                        new DeconstructedObject.CharField(name, annotatedType, dataContainer.readChar());
            } else if (attribute.type() == byte.class) {
                reader = dataContainer ->
                        new DeconstructedObject.ByteField(name, annotatedType, dataContainer.readByte());
            } else if (attribute.type() == short.class) {
                reader = dataContainer ->
                        new DeconstructedObject.ShortField(name, annotatedType, dataContainer.readShort());
            } else if (attribute.type() == int.class) {
                reader = dataContainer ->
                        new DeconstructedObject.IntField(name, annotatedType, dataContainer.readInt());
            } else if (attribute.type() == long.class) {
                reader = dataContainer ->
                        new DeconstructedObject.LongField(name, annotatedType, dataContainer.readLong());
            } else if (attribute.type() == float.class) {
                reader = dataContainer ->
                        new DeconstructedObject.FloatField(name, annotatedType, dataContainer.readFloat());
            } else if (attribute.type() == double.class) {
                reader = dataContainer ->
                        new DeconstructedObject.DoubleField(name, annotatedType, dataContainer.readDouble());
            } else {
                reader = dataContainer ->
                        new DeconstructedObject.ObjectField(name, attribute.type(), annotatedType, dataContainer.readObject());
            }
            fieldReaders[i] = reader;
        }
        return new FieldsExtractor(fieldReaders);
    }

    /**
     * Reads fields from a container.
     *
     * @param dataContainer container to read
     * @return list of fields
     */
    List<DeconstructedObject.Field> read(ModelDataContainer dataContainer) {
        List<DeconstructedObject.Field> fields = new ArrayList<>(fieldReaders.length);
        for (var reader : fieldReaders) {
            fields.add(reader.apply(dataContainer));
        }
        return fields;
    }

}
