package org.machinemc.foundry;

import org.jetbrains.annotations.Nullable;

/**
 * Defines a contract for objects that can own a {@link Metadata} instance.
 *
 * @see Metadata
 */
public interface MetadataHolder {

    /**
     * Retrieves the {@link Metadata} container associated with this object.
     * <p>
     * Implementations need consistently return the same instance
     * for the lifetime of the object and ensure the returned value is never null.
     *
     * @return metadata instance belonging to this holder
     */
    Metadata getMetadata();

    /**
     * A convenience method to copy all metadata from another {@code MetadataHolder} into this one.
     *
     * @param other the source from which to copy metadata
     */
    default void copyMetadata(@Nullable MetadataHolder other) {
        if (other == null) return;
        getMetadata().insert(other.getMetadata());
    }

}
