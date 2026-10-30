package com.flowforge.etl.core.execution;

/** Final status of a step in a completed pipeline execution. */
public enum StepExecutionStatus {
    SUCCEEDED,
    FAILED,
    SKIPPED
}
