package com.flowforge.etl.core.execution;

import java.time.Instant;

/**
 * Immutable diagnostic record for one step in a pipeline execution.
 *
 * @param stepId step identifier
 * @param stepName human-readable step name
 * @param status step lifecycle status
 * @param startedAt step start time; null when the step was skipped
 * @param finishedAt step finish time; null only while started
 * @param exceptionType fully qualified exception type, when available
 * @param errorMessage failure message, when available
 * @param causeType fully qualified root cause type, when available
 * @param causeMessage root cause message, when available
 */
public record StepExecutionRecord(
        String stepId,
        String stepName,
        StepExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        String exceptionType,
        String errorMessage,
        String causeType,
        String causeMessage) {

    public StepExecutionRecord {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("Step ID must not be blank");
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("Step name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Step status must not be null");
        }
        if (status == StepExecutionStatus.STARTED) {
            if (startedAt == null || finishedAt != null) {
                throw new IllegalArgumentException(
                        "Started steps require a start timestamp and no finish timestamp");
            }
        } else {
            if (finishedAt == null) {
                throw new IllegalArgumentException(
                        "Finished steps must include a finish timestamp");
            }
            if (status != StepExecutionStatus.SKIPPED && startedAt == null) {
                throw new IllegalArgumentException(
                        "Executed steps must include a start timestamp");
            }
            if (startedAt != null && finishedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException(
                        "Step finish timestamp must not be before start timestamp");
            }
        }
        if (status == StepExecutionStatus.FAILED
                && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException(
                    "Failed steps must include an error message");
        }
        if (status != StepExecutionStatus.FAILED
                && (exceptionType != null || errorMessage != null
                || causeType != null || causeMessage != null)) {
            throw new IllegalArgumentException(
                    "Only failed steps may contain exception details");
        }
    }
}
