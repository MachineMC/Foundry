package org.machinemc.foundry.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Class that is responsible for constructing and deconstructing objects,
 * writing their data into {@link ModelDataContainer}.
 * <p>
 * This class is for internal use only.
 *
 * @param <T> object type
 */
@ApiStatus.Internal
public abstract class ObjectFactory<T> {

    /**
     * Creates new object factory for objects of given type.
     *
     * @param type type of the object
     * @return object factory for objects of given type
     * @param <T> object type
     */
    public static <T> ObjectFactory<T> create(Class<T> type) {
        return create(type, ClassModel.of(type));
    }

    /**
     * Creates new object factory for objects of given type.
     *
     * @param type type of the object
     * @param classModel class model of the type
     * @return object factory for objects of given type
     * @param <T> object type
     */
    public static <T> ObjectFactory<T> create(Class<T> type, ClassModel<T> classModel) {
        //noinspection unchecked
        return (ObjectFactory<T>) ObjectFactoryGenerator.generate(type, classModel);
    }

    private final ModelDataContainer.Factory holderFactory;

    /**
     * @param classModel class model for the type of this factory
     */
    protected ObjectFactory(ClassModel<T> classModel) {
        holderFactory = ModelDataContainer.Factory.of(classModel);
    }

    /**
     * @return new empty model data container for the type of this factory
     */
    protected ModelDataContainer newContainer() {
        return holderFactory.get();
    }

    /**
     * Creates new model data container and writes the data of given object to it.
     *
     * @param instance object to write the data from
     * @return new model data container with written data from the object
     */
    public abstract ModelDataContainer write(T instance);

    /**
     * Creates new instance of the factory' type and populates it with the
     * data in given model data container.
     *
     * @param container container
     * @return new instance with data read from the container
     */
    public abstract T read(ModelDataContainer container);

    /**
     * Part of object factory that reads or writes some attributes.
     * <p>
     * This class does not handle the instance or data container creation
     * but only individual read or writes.
     */
    public interface ObjectFactoryPart<T> {

        /**
         * Writes some of {@code instance} data to the provided container.
         *
         * @param instance instance to read the data from
         * @param container container to write the data to
         */
        void write(T instance, ModelDataContainer container);

        /**
         * Reads some data of the provided container and writes them to the {@code instance}.
         *
         * @param container container to read the data from
         * @param instance instance to write the data to
         */
        void read(ModelDataContainer container, T instance);

    }

}
