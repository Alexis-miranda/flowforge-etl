package com.flowforge.etl.core.execution;

/**
 * Step state observed during recovery assessment.
 * NOT_STARTED is synthesized when no durable step row exists; it is not stored in SQL.
 */
public enum PersistedStepExecutionStatus {
    NOT_STARTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
