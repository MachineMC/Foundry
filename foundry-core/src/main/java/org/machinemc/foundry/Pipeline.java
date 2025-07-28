package org.machinemc.foundry;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.ThreadSafe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;

/**
 * Represents an immutable, type-safe pipeline for processing data, composed of {@link DataHandler}s.
 * <p>
 * A pipeline has a defined input type {@link I} and output type {@link O}.
 * <p>
 * Handlers are executed in the order they are added.
 *
 * @param <I> the input type of the entire pipeline
 * @param <O> the final output type of the entire pipeline
 */
@Immutable
@ThreadSafe
public final class Pipeline<I, O> {

    private final @Unmodifiable List<DataHandler<?, ?>> handlers;

    private Pipeline(SequencedCollection<DataHandler<?, ?>> handlers) {
        this.handlers = Collections.unmodifiableList(new LinkedList<>(handlers));
    }

    /**
     * Creates a new builder to construct a pipeline.
     *
     * @param <F> the input type of the first handler (and the pipeline)
     * @return a new builder instance
     */
    public static <F> Builder<F, F> builder() {
        return builder(DataHandler.identity());
    }

    /**
     * Creates a new builder to construct a pipeline, starting with the first handler.
     *
     * @param handler the first data handler, which defines the initial input type
     * @param <F> the input type of the first handler (and the pipeline)
     * @param <L> the output type of the first handler
     * @return a new builder instance
     */
    public static <F, L> Builder<F, L> builder(DataHandler<F, L> handler) {
        return new Builder<>(handler);
    }

    /**
     * Composes compatible pipelines into a single pipeline.
     *
     * @return new, combined pipeline.
     */
    public static <A, B, C> Pipeline<A, C> compose(Pipeline<A, B> p1, Pipeline<B, C> p2) {
        return compose(List.of(p1, p2));
    }

    /**
     * Composes compatible pipelines into a single pipeline.
     *
     * @return new, combined pipeline.
     */
    public static <A, B, C, D> Pipeline<A, D> compose(Pipeline<A, B> p1, Pipeline<B, C> p2, Pipeline<C, D> p3) {
        return compose(List.of(p1, p2, p3));
    }

    /**
     * Composes compatible pipelines into a single pipeline.
     *
     * @return new, combined pipeline.
     */
    public static <A, B, C, D, E> Pipeline<A, E> compose(Pipeline<A, B> p1, Pipeline<B, C> p2, Pipeline<C, D> p3, Pipeline<D, E> p4) {
        return compose(List.of(p1, p2, p3, p4));
    }

    /**
     * Composes compatible pipelines into a single pipeline.
     *
     * @return new, combined pipeline.
     */
    public static <A, B, C, D, E, F> Pipeline<A, F> compose(Pipeline<A, B> p1,Pipeline<B, C> p2, Pipeline<C, D> p3, Pipeline<D, E> p4, Pipeline<E, F> p5) {
        return compose(List.of(p1, p2, p3, p4, p5));
    }

    private static <First, Last> Pipeline<First, Last> compose(List<Pipeline<?, ?>> pipelines) {
        List<DataHandler<?, ?>> combined = new LinkedList<>();
        pipelines.forEach(p -> combined.addAll(p.handlers));
        return new Pipeline<>(combined);
    }

    /**
     * Processes the given input through the entire pipeline, transforming it through each handler in order.
     *
     * @param input the initial data to process
     * @return the result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public O process(I input) throws Exception {
        Object currentData = input;
        for (DataHandler handler : handlers) currentData = handler.transform(currentData);
        return (O) currentData;
    }

    /**
     * A builder for creating {@link Pipeline} instances with compile-time type safety.
     *
     * @param <I> the input type of the pipeline being built
     * @param <Current> the output type of the last handler added to the builder
     */
    public static class Builder<I, Current> {

        private final List<DataHandler<?, ?>> handlers = new LinkedList<>();

        private Builder(DataHandler<I, Current> firstHandler) {
            Preconditions.checkNotNull(firstHandler, "First handler cannot be null");
            handlers.add(firstHandler);
        }

        /**
         * Adds a new {@link DataHandler} to the end of the pipeline.
         *
         * @param handler handler to add
         * @param <Next> next output type
         * @return this
         */
        @Contract("_ -> this")
        @SuppressWarnings("unchecked")
        public <Next> Builder<I, Next> next(DataHandler<Current, Next> handler) {
            Preconditions.checkNotNull(handler, "Next handler cannot be null");
            handlers.add(handler);
            return (Builder<I, Next>) this;
        }

        /**
         * Chains an existing Pipeline to the end of the current build sequence.
         * <p>
         * The pipeline's input type must match the output type of the previously added handler or pipeline.
         *
         * @param pipeline the pipeline to chain
         * @param <Next> next output type
         * @return this
         */
        @Contract("_ -> this")
        @SuppressWarnings("unchecked")
        public <Next> Builder<I, Next> next(Pipeline<Current, Next> pipeline) {
            Preconditions.checkNotNull(pipeline, "Pipeline to be chained cannot be null");
            handlers.addAll(pipeline.handlers);
            return (Builder<I, Next>) this;
        }

        /**
         * Builds the immutable {@link Pipeline}.
         */
        public Pipeline<I, Current> build() {
            return new Pipeline<>(this.handlers);
        }

    }

}
