package com.flowforge.etl.messaging.consumer;

/** Indicates that another worker currently owns the execution lease. */
public final class ExecutionLeaseUnavailableException extends RuntimeException {

    public ExecutionLeaseUnavailableException(String executionId) {
        super("Execution lease is currently unavailable: " + executionId);
    }
}
