package org.machinemc.foundry;

/**
 * Represents a single processing step within a {@link Pipeline}.
 * <p>
 * {@link DataHandler} is responsible for transforming an input data type {@link I} (from) into an output data type {@link O} (to).
 * <p>
 * Each handler performs a specific operation, such as encoding, decoding, compression, or data validation.
 *
 * @param <I> input data type this handler accepts
 * @param <O> output data type this handler produces
 */
@FunctionalInterface
public interface DataHandler<I, O> {

    /**
     * Returns a {@link DataHandler} that performs an identity transformation.
     * That is, it returns the same instance as it receives.
     *
     * @param <T> data type
     * @return identity data handler
     */
    static <T> DataHandler<T, T> identity() {
        return o -> o;
    }

    /**
     * Processes the given instance of type {@link I} and transforms it into an instance of type {@link O}.
     *
     * @param instance input data
     * @return transformed data
     */
    O transform(I instance) throws Exception;

}
