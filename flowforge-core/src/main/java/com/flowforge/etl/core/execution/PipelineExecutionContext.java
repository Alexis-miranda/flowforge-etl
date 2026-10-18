
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
     * Returns an unmodifiable view of the stored data.
     *
     * @return an unmodifiable map of the context data
     */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(data);
    }
}
