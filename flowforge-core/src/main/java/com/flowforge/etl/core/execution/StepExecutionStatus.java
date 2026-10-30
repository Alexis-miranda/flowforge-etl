package com.flowforge.etl.core.execution;

/** Lifecycle status of an individual pipeline step. */
public enum StepExecutionStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
