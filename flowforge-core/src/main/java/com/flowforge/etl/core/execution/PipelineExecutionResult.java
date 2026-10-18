
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;
import java.time.Duration;

import java.time.Instant;

/**
 * Represents the result of a pipeline execution.
 *
 * @param executionId the unique identifier of the execution
 * @param pipelineId the identifier of the executed pipeline
 * @param status the final execution status
 * @param failedStepId the identifier of the failed step, if any
 * @param errorMessage the error message, if any
 * @param startedAt the execution start timestamp
 * @param finishedAt the execution completion timestamp
 */
public record PipelineExecutionResult(
        String executionId,
        String pipelineId,
        PipelineStatus status,
        String failedStepId,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    /**
     * Validates the execution result.
     */
    public PipelineExecutionResult {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Execution ID must not be blank");
        }

        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be blank");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Pipeline status must not be null");
        }

        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException(
                    "Execution timestamps must not be null");
        }

        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Execution finish timestamp must not be before start timestamp");
        }

        if (status == PipelineStatus.RUNNING
                || status == PipelineStatus.CREATED) {
            throw new IllegalArgumentException(
                    "Execution result must have a final status");
        }

        if (status == PipelineStatus.FAILED
                && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException(
                    "Failed executions must include an error message");
        }

        if (status != PipelineStatus.FAILED
                && (failedStepId != null || errorMessage != null)) {
            throw new IllegalArgumentException(
                    "Successful executions must not contain failure details");
        }
    }

    /**
     * Indicates whether the pipeline completed successfully.
     *
     * @return true if the execution succeeded
     */
    public boolean succeeded() {
        return status == PipelineStatus.SUCCEEDED;
    }

    /**
     * Calculates the total execution duration.
     *
     * @return the execution duration
     */
    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }
}
