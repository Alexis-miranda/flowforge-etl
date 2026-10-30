package com.flowforge.etl.core.execution;

import java.time.Instant;

/**
 * Immutable diagnostic record for one step in a completed pipeline execution.
 *
 * @param stepId step identifier
 * @param stepName human-readable step name
 * @param status final step status
 * @param startedAt step start time; null when skipped
 * @param finishedAt step finish time
 * @param exceptionType fully qualified exception type, when available
 * @param errorMessage failure message, when available
 * @param causeType fully qualified root cause type, when available
 * @param causeMessage root cause message, when available
 * @param skipReason explanation for a skipped step, when applicable
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
        String causeMessage,
        String skipReason) {

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
        if (finishedAt == null) {
            throw new IllegalArgumentException(
                    "Completed steps must include a finish timestamp");
        }
        if (status != StepExecutionStatus.SKIPPED && startedAt == null) {
            throw new IllegalArgumentException(
                    "Executed steps must include a start timestamp");
        }
        if (startedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Step finish timestamp must not be before start timestamp");
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
        if (status == StepExecutionStatus.SKIPPED
                && (skipReason == null || skipReason.isBlank())) {
            throw new IllegalArgumentException(
                    "Skipped steps must include a reason");
        }
        if (status != StepExecutionStatus.SKIPPED && skipReason != null) {
            throw new IllegalArgumentException(
                    "Only skipped steps may include a skip reason");
        }
    }
}
