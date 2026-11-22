package com.flowforge.etl.core.execution;

import java.time.Instant;

/** Immutable projection of one step row read from the execution audit. */
public record PersistedStepExecution(
        String stepId,
        PersistedStepExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        FailureClassification failureClassification) {

    public PersistedStepExecution {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("Step ID must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Persisted step status must not be null");
        }
        if (status == PersistedStepExecutionStatus.RUNNING) {
            if (startedAt == null || finishedAt != null) {
                throw new IllegalArgumentException(
                        "Running steps must have a start timestamp and no finish timestamp");
            }
        } else if (finishedAt == null) {
            throw new IllegalArgumentException(
                    "Terminal step records must include a finish timestamp");
        }
        if (status == PersistedStepExecutionStatus.SKIPPED && startedAt != null) {
            throw new IllegalArgumentException("Skipped steps must not have a start timestamp");
        }
        if (status != PersistedStepExecutionStatus.SKIPPED && startedAt == null) {
            throw new IllegalArgumentException("Executed steps must include a start timestamp");
        }
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Step finish timestamp must not be before start timestamp");
        }
        if (status == PersistedStepExecutionStatus.FAILED && failureClassification == null) {
            throw new IllegalArgumentException("Failed steps must include a failure classification");
        }
        if (status != PersistedStepExecutionStatus.FAILED && failureClassification != null) {
            throw new IllegalArgumentException("Only failed steps may include a failure classification");
        }
    }
}
