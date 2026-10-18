
package com.flowforge.etl.core.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stores shared data during a pipeline execution.
 */
public class PipelineExecutionContext {

    private final Map<String, Object> data = new HashMap<>();

    /**
     * Stores a value in the execution context.
     *
     * @param key the data key
     * @param value the value to store
     */
    public void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Context key must not be blank");
        }

        data.put(key, value);
    }

    /**
     * Retrieves a value from the execution context.
     *
     * @param key the data key
     * @return the stored value, or null if the key does not exist
     */
    public Object get(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Context key must not be blank");
        }

        return data.get(key);
    }

    /**
     * Retrieves a value from the execution context and checks its type.
     *
     * @param key the data key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the stored value cast to the expected type, or null if absent
     * @throws IllegalArgumentException if the key or type is invalid
     * @throws ClassCastException if the stored value has an incompatible type
     */
    public <T> T get(String key, Class<T> type) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Context key must not be blank");
        }

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
     * Returns an unmodifiable view of the stored data.
     *
     * @return an unmodifiable map of the context data
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(data);
    }
}
