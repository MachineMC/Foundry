package org.machinemc.foundry;

import org.jetbrains.annotations.Contract;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Container for storing and retrieving metadata.
 * <p>
 * This class provides a simple key-value store and uses a {@link String} key to identify entry.
 * <p>
 */
public final class Metadata implements Cloneable {

    private final Map<String, Object> data = new HashMap<>();

    public Metadata() {
    }

    private Metadata(Metadata other) {
        data.putAll(other.data);
    }

    /**
     * Retrieves a value from the metadata store.
     *
     * @param key key
     * @param type requested data type
     * @param <T> requested data type
     * @return value associated with the given key if its type is compatible with the requested one, else empty
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object found = data.get(key);
        if (!type.isInstance(found)) return Optional.empty();
        return Optional.of((T) found);
    }

    /**
     * Stores a key-value pair in the metadata store.
     * <p>
     * If a value already exists for the given key, it will be overwritten with the new data.
     * <p>
     * If null is provided, the entry is removed from the metadata.
     *
     * @param key key
     * @param data the data to store
     * @param <T>  type of the data being stored
     * @return this
     */
    @Contract("_, _ -> this")
    public <T> Metadata set(String key, T data) {
        if (data != null)
            this.data.put(key, data);
        else
            this.data.remove(key);
        return this;
    }

    /**
     * Copies all key-value pairs from another {@link Metadata} instance into this one.
     * <p>
     * If any keys in the source metadata already exist in this metadata, their values
     * will be overwritten.
     *
     * @param other the metadata instance to copy entries from. If null, this method has no effect.
     * @return this
     */
    @Contract("_ -> this")
    public Metadata insert(Metadata other) {
        if (other != null) data.putAll(other.data);
        return this;
    }

    /**
     * Removes the key-value pair associated with the given key.
     * If the key does not exist, this method has no effect.
     *
     * @param key the key of the entry to remove
     */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * Checks if a value is present for the given key.
     *
     * @param key the key to check
     * @return {@code true} if a value is associated with the key, else {@code false}
     */
    public boolean containsKey(String key) {
        return this.data.containsKey(key);
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Metadata clone() {
        return new Metadata(this);
    }

}
