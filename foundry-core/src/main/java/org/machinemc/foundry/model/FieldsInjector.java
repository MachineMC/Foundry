package org.machinemc.foundry.model;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Optimized writer of deconstructed object fields into a {@link ModelDataContainer}.
 * <p>
 * This class is for internal use only.
 *
 * @param fieldWriters field writers
 */
record FieldsInjector(BiConsumer<DeconstructedObject.Field, ModelDataContainer>[] fieldWriters) {

    /**
     * Creates new fields injector instance for given class model.
     *
     * @param model model
     * @return fields injector for given model
     */
    static FieldsInjector of(ClassModel<?> model) {
        ModelAttribute[] attributes = model.getAttributes();
        //noinspection unchecked
        BiConsumer<DeconstructedObject.Field, ModelDataContainer>[] fieldWriters = new BiConsumer[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            ModelAttribute attribute = attributes[i];
            BiConsumer<DeconstructedObject.Field, ModelDataContainer> writer;
            if (attribute.type() == boolean.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeBool(((DeconstructedObject.BoolField) field).value());
            } else if (attribute.type() == char.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeChar(((DeconstructedObject.CharField) field).value());
            } else if (attribute.type() == byte.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeByte(((DeconstructedObject.ByteField) field).value());
            } else if (attribute.type() == short.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeShort(((DeconstructedObject.ShortField) field).value());
            } else if (attribute.type() == int.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeInt(((DeconstructedObject.IntField) field).value());
            } else if (attribute.type() == long.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeLong(((DeconstructedObject.LongField) field).value());
            } else if (attribute.type() == float.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeFloat(((DeconstructedObject.FloatField) field).value());
            } else if (attribute.type() == double.class) {
                writer = (field, dataContainer) ->
                        dataContainer.writeDouble(((DeconstructedObject.DoubleField) field).value());
            } else {
                writer = (field, dataContainer) ->
                        dataContainer.writeObject(((DeconstructedObject.ObjectField) field).value());
            }
            fieldWriters[i] = writer;
        }
        return new FieldsInjector(fieldWriters);
    }

    /**
     * Writes provided fields into a container.
     *
     * @param fields fields to write
     * @param dataContainer data container
     */
    void write(List<DeconstructedObject.Field> fields, ModelDataContainer dataContainer) {
        for (int i = 0; i < fieldWriters.length; i++) {
            fieldWriters[i].accept(fields.get(i), dataContainer);
        }
    }

}
