package com.flowforge.etl.core.execution;

/** Status observed in the durable step-execution audit. */
public enum PersistedStepExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
