package org.machinemc.foundry;

import com.google.common.base.Preconditions;

/**
 * Represents a bidirectional converter that can transform data between two types.
 * <p>
 * A Codec is composed of two {@link Pipeline}s, one for encoding (from {@link A} to {@link B})
 * and one for decoding (from {@link B} to {@link A}).
 *
 * @param encode the {@link Pipeline} that transforms type A to type B.
 * @param decode the {@link Pipeline} that transforms type B back to type A
 * @param <A> the first type
 * @param <B> the second type
 */
public record Codec<A, B>(Pipeline<A, B> encode, Pipeline<B, A> decode) {

    /**
     * Constructs a new Codec.
     *
     * @param encode The pipeline for encoding (A -> B)
     * @param decode The pipeline for decoding (B -> A)
     */
    public Codec {
        Preconditions.checkNotNull(encode, "Encoding pipeline can not be null");
        Preconditions.checkNotNull(decode, "Decoding pipeline can not be null");
    }

    /**
     * Composes two codecs into a single codec.
     * <p>
     * For example, given a {@code Codec<A, B>} and a {@code Codec<B, C>}, this will produce a {@code Codec<A, C>}.
     * The new encoding pipeline will be A -> B -> C.
     * The new decoding pipeline will be C -> B -> A.
     *
     * @param c1 the first codec in the chain (A -> B)
     * @param c2 the second codec in the chain (B -> C)
     * @return new composed {@code Codec<A, C>}
     * @param <A> the source type of the first codec
     * @param <B> the intermediate type shared between both codecs
     * @param <C> the target type of the second codec
     */
    public static <A, B, C> Codec<A, C> compose(Codec<A, B> c1, Codec<B, C> c2) {
        Pipeline<A, C> newEncode = Pipeline.compose(c1.encode(), c2.encode());
        Pipeline<C, A> newDecode = Pipeline.compose(c2.decode(), c1.decode());
        return new Codec<>(newEncode, newDecode);
    }

    /**
     * Composes codecs into a single codec.
     *
     * @return new composed codec
     * @see #compose(Codec, Codec)
     */
    public static <A, B, C, D> Codec<A, D> compose(Codec<A, B> c1, Codec<B, C> c2, Codec<C, D> c3) {
        return compose(compose(c1, c2), c3);
    }

    /**
     * Composes codecs into a single codec.
     *
     * @return new composed codec
     * @see #compose(Codec, Codec)
     */
    public static <A, B, C, D, E> Codec<A, E> compose(Codec<A, B> c1, Codec<B, C> c2, Codec<C, D> c3, Codec<D, E> c4) {
        return compose(compose(c1, c2, c3), c4);
    }

    /**
     * Composes codecs into a single codec.
     *
     * @return new composed codec
     * @see #compose(Codec, Codec)
     */
    public static <A, B, C, D, E, F> Codec<A, F> compose(Codec<A, B> c1, Codec<B, C> c2, Codec<C, D> c3, Codec<D, E> c4, Codec<E, F> c5) {
        return compose(compose(c1, c2, c3, c4), c5);
    }

    /**
     * Joins another codec to this one.
     *
     * @param other other codec to join
     * @return new composed codec
     */
    public <C> Codec<A, C> join(Codec<B, C> other) {
        return compose(this, other);
    }

    /**
     * Processes the given object through the encoding pipeline.
     *
     * @param obj the object to encode
     * @return the encoded object
     */
    public B encode(A obj) throws Exception {
        return encode.process(obj);
    }

    /**
     * Processes the given object through the decoding pipeline.
     *
     * @param obj the object to decode
     * @return the decoded object
     */
    public A decode(B obj) throws Exception {
        return decode.process(obj);
    }

}
