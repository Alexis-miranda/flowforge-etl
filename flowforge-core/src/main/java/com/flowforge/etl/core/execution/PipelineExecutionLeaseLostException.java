package com.flowforge.etl.core.execution;

/** Signals that a worker lost its execution lease and must stop processing. */
public final class PipelineExecutionLeaseLostException extends RuntimeException {

    public PipelineExecutionLeaseLostException(String executionId, Throwable cause) {
        super("Execution lease is no longer valid: " + executionId, cause);
    }

    public PipelineExecutionLeaseLostException(String executionId) {
        super("Execution lease is no longer valid: " + executionId);
    }
}
