package com.flowforge.etl.core.execution;

/**
 * Indicates that an execution with the supplied identifier was already
 * registered and therefore must not be executed again.
 */
public final class DuplicatePipelineExecutionException extends RuntimeException {

    private final String executionId;

    public DuplicatePipelineExecutionException(String executionId) {
        super("Pipeline execution already exists: " + executionId);
        this.executionId = executionId;
    }

    public String executionId() {
        return executionId;
    }
}
