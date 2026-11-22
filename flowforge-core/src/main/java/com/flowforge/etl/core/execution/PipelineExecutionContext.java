package com.flowforge.etl.core.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Stores shared data during a pipeline execution. */
public class PipelineExecutionContext {
    private final Map<String, Object> data = new HashMap<>();
    private final String executionId;
    private final PipelineExecutionLease executionLease;

    /** Creates an execution context without identity metadata. */
    public PipelineExecutionContext() { this(null, null); }

    /** Creates an execution context carrying a lease and its execution identity. */
    public PipelineExecutionContext(PipelineExecutionLease executionLease) {
        this(executionLease == null ? null : executionLease.executionId(), executionLease);
    }

    /**
     * Creates an execution context with an execution identity and optional lease.
     * @param executionId stable identifier for this execution, or null
     * @param executionLease lease snapshot, or null for local execution
     */
    public PipelineExecutionContext(String executionId, PipelineExecutionLease executionLease) {
        if (executionId != null && executionId.isBlank()) {
            throw new IllegalArgumentException("Execution id must not be blank");
        }
        if (executionLease != null && !executionLease.executionId().equals(executionId)) {
            throw new IllegalArgumentException("Execution id must match the supplied lease");
        }
        this.executionId = executionId;
        this.executionLease = executionLease;
    }

    /** Returns the stable execution identifier, if available. */
    public Optional<String> executionId() { return Optional.ofNullable(executionId); }

    /**
     * Returns the immutable lease snapshot, if supplied. Its presence does not
     * prove current ownership; side effects must validate the fencing token.
     */
    public Optional<PipelineExecutionLease> executionLease() {
        return Optional.ofNullable(executionLease);
    }

    public void put(String key, Object value) {
        validateKey(key);
        if (value == null) throw new IllegalArgumentException("Context value must not be null");
        data.put(key, value);
    }

    public Object get(String key) {
        validateKey(key);
        return data.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        validateKey(key);
        if (type == null) throw new IllegalArgumentException("Expected type must not be null");
        Object value = data.get(key);
        return value == null ? null : type.cast(value);
    }

    public <T> T getRequired(String key, Class<T> type) {
        validateKey(key);
        if (type == null) throw new IllegalArgumentException("Expected type must not be null");
        Object value = data.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required context value: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Context value for key '" + key
                    + "' must be of type " + type.getSimpleName());
        }
        return type.cast(value);
    }

    public boolean contains(String key) {
        validateKey(key);
        return data.containsKey(key);
    }

    public Object remove(String key) {
        validateKey(key);
        return data.remove(key);
    }

    public void clear() { data.clear(); }

    public Map<String, Object> asMap() { return Collections.unmodifiableMap(data); }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Context key must not be blank");
        }
    }
}