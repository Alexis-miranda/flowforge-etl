package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Represents the result of a pipeline execution.
 *
 * @param executionId unique identifier of the execution
 * @param pipelineId identifier of the executed pipeline
 * @param status final execution status
 * @param failedStepId identifier of the failed step, if any
 * @param errorMessage failure message, if any
 * @param startedAt execution start timestamp
 * @param finishedAt execution completion timestamp
 * @param stepHistory ordered diagnostic history of pipeline steps
 */
public record PipelineExecutionResult(
        String executionId,
        String pipelineId,
        PipelineStatus status,
        String failedStepId,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        List<StepExecutionRecord> stepHistory) {

    /**
     * Retains source compatibility with the original result constructor.
     */
    public PipelineExecutionResult(
            String executionId,
            String pipelineId,
            PipelineStatus status,
            String failedStepId,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {
        this(executionId, pipelineId, status, failedStepId, errorMessage,
                startedAt, finishedAt, List.of());
    }

    public PipelineExecutionResult {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("Pipeline ID must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("Pipeline status must not be null");
        }
        if (startedAt == null || finishedAt == null) {
            throw new IllegalArgumentException("Execution timestamps must not be null");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Execution finish timestamp must not be before start timestamp");
        }
        if (status == PipelineStatus.RUNNING || status == PipelineStatus.CREATED) {
            throw new IllegalArgumentException("Execution result must have a final status");
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
        stepHistory = List.copyOf(stepHistory);
    }

    public boolean succeeded() {
        return status == PipelineStatus.SUCCEEDED;
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }
}
