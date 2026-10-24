
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.time.Instant;

/**
 * Represents a snapshot of a pipeline execution,
 * including executions that are still running.
 *
 * @param executionId unique execution identifier
 * @param pipelineId identifier of the pipeline
 * @param status current execution status
 * @param failedStepId identifier of the failed step, if any
 * @param errorMessage error message, if any
 * @param startedAt execution start timestamp
 * @param finishedAt execution completion timestamp, or null while running
 */
public record PipelineExecutionSnapshot(
        String executionId,
        String pipelineId,
        PipelineStatus status,
        String failedStepId,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    public PipelineExecutionSnapshot {
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

        if (startedAt == null) {
            throw new IllegalArgumentException(
                    "Execution start timestamp must not be null");
        }

        if (status == PipelineStatus.RUNNING) {
            if (finishedAt != null) {
                throw new IllegalArgumentException(
                        "Running executions must not have a finish timestamp");
            }

            if (failedStepId != null || errorMessage != null) {
                throw new IllegalArgumentException(
                        "Running executions must not contain failure details");
            }
        } else {
            if (status == PipelineStatus.CREATED) {
                throw new IllegalArgumentException(
                        "Execution snapshot must not have CREATED status");
            }

            if (finishedAt == null) {
                throw new IllegalArgumentException(
                        "Finished executions must have a finish timestamp");
            }

            if (finishedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException(
                        "Execution finish timestamp must not be before start timestamp");
            }

            if (status == PipelineStatus.FAILED) {
                if (errorMessage == null || errorMessage.isBlank()) {
                    throw new IllegalArgumentException(
                            "Failed executions must include an error message");
                }
            } else if (failedStepId != null || errorMessage != null) {
                throw new IllegalArgumentException(
                        "Successful executions must not contain failure details");
            }
        }
    }

    public boolean finished() {
        return status != PipelineStatus.RUNNING;
    }
}
