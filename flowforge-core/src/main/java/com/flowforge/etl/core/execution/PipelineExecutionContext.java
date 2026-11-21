package com.flowforge.etl.core.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores shared data during a pipeline execution.
 *
 * <p>Values are stored as objects because pipeline steps exchange different
 * kinds of data. Callers can use the typed accessors to validate values when
 * retrieving them.</p>
 */
public class PipelineExecutionContext {

    private final Map<String, Object> data = new HashMap<>();

    /**
     * Stores a non-null value in the execution context.
     *
     * @param key the data key
     * @param value the value to store
     * @throws IllegalArgumentException if the key is null or blank,
     *                                  or the value is null
     */
    public void put(String key, Object value) {
        validateKey(key);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Context value must not be null");
        }

        data.put(key, value);
    }

    /**
     * Retrieves a value from the execution context without checking its type.
     *
     * @param key the data key
     * @return the stored value, or null if the key does not exist
     * @throws IllegalArgumentException if the key is null or blank
     */
    public Object get(String key) {
        validateKey(key);
        return data.get(key);
    }

    /**
     * Retrieves an optional value from the execution context and checks its type.
     *
     * @param key the data key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the stored value cast to the expected type, or null if absent
     * @throws IllegalArgumentException if the key or type is invalid
     * @throws ClassCastException if the stored value has an incompatible type
     */
    public <T> T get(String key, Class<T> type) {
        validateKey(key);

        if (type == null) {
            throw new IllegalArgumentException(
                    "Expected type must not be null");
        }

        Object value = data.get(key);

        if (value == null) {
            return null;
        }

        return type.cast(value);
    }

    /**
     * Retrieves a required value from the execution context and checks its type.
     *
     * <p>Unlike {@link #get(String, Class)}, this method rejects both a missing
     * value and a value with an incompatible type using
     * {@link IllegalArgumentException}.</p>
     *
     * @param key the data key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the stored value cast to the expected type
     * @throws IllegalArgumentException if the key or type is invalid, the value
     *                                  is missing, or its type is incompatible
     */
    public <T> T getRequired(String key, Class<T> type) {
        validateKey(key);

        if (type == null) {
            throw new IllegalArgumentException(
                    "Expected type must not be null");
        }

        Object value = data.get(key);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required context value: " + key);
        }

        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Context value for key '" + key
                            + "' must be of type " + type.getSimpleName());
        }

        return type.cast(value);
    }

    /**
     * Checks whether the execution context contains the given key.
     *
     * @param key the data key
     * @return true if the key exists, false otherwise
     * @throws IllegalArgumentException if the key is null or blank
     */
    public boolean contains(String key) {
        validateKey(key);
        return data.containsKey(key);
    }

    /**
     * Removes a value from the execution context.
     *
     * @param key the data key
     * @return the removed value, or null if the key did not exist
     * @throws IllegalArgumentException if the key is null or blank
     */
    public Object remove(String key) {
        validateKey(key);
        return data.remove(key);
    }

    /**
     * Removes all entries from the execution context.
     */
    public void clear() {
        data.clear();
    }

    /**
     * Returns an unmodifiable, live view of the stored data.
     *
     * <p>The returned map cannot be modified directly, but it reflects
     * subsequent changes made through this context.</p>
     *
     * @return an unmodifiable view of the context data
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(data);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Context key must not be blank");
        }
    }
}
