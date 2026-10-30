package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the final result of a pipeline execution.
 *
 * @param executionId unique identifier of the execution
 * @param pipelineId identifier of the executed pipeline
 * @param status final execution status
 * @param failedStepId identifier of the failed step, if any
 * @param errorMessage failure message, if any
 * @param startedAt execution start timestamp
 * @param finishedAt execution completion timestamp
 * @param stepHistory ordered final diagnostic history of pipeline steps
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
        validateStepHistory(status, failedStepId, stepHistory);
    }

    private static void validateStepHistory(
            PipelineStatus pipelineStatus,
            String failedStepId,
            List<StepExecutionRecord> stepHistory) {

        // An empty history is allowed for results created through the legacy constructor.
        if (stepHistory.isEmpty()) {
            return;
        }

        Set<String> stepIds = new HashSet<>();
        int failedIndex = -1;

        for (int index = 0; index < stepHistory.size(); index++) {
            StepExecutionRecord record = stepHistory.get(index);
            if (!stepIds.add(record.stepId())) {
                throw new IllegalArgumentException(
                        "Step history must not contain duplicate step IDs: "
                                + record.stepId());
            }

            if (record.status() == StepExecutionStatus.FAILED) {
                if (failedIndex >= 0) {
                    throw new IllegalArgumentException(
                            "Sequential pipeline history must contain at most one failed step");
                }
                failedIndex = index;
            }
        }

        if (pipelineStatus == PipelineStatus.SUCCEEDED) {
            if (failedIndex >= 0 || stepHistory.stream().anyMatch(
                    record -> record.status() != StepExecutionStatus.SUCCEEDED)) {
                throw new IllegalArgumentException(
                        "Successful executions may only contain succeeded steps");
            }
            return;
        }

        if (failedIndex < 0
                || !stepHistory.get(failedIndex).stepId().equals(failedStepId)) {
            throw new IllegalArgumentException(
                    "Failed execution history must identify its failed step");
        }
        if (!stepHistory.get(failedIndex).errorMessage().equals(errorMessage)) {
            throw new IllegalArgumentException(
                    "Pipeline error message must match the failed step error message");
        }

        for (int index = 0; index < failedIndex; index++) {
            if (stepHistory.get(index).status() != StepExecutionStatus.SUCCEEDED) {
                throw new IllegalArgumentException(
                        "Steps before the failed step must have succeeded");
            }
        }
        for (int index = failedIndex + 1; index < stepHistory.size(); index++) {
            if (stepHistory.get(index).status() != StepExecutionStatus.SKIPPED) {
                throw new IllegalArgumentException(
                        "Steps after the failed step must be skipped");
            }
        }
    }

    public boolean succeeded() {
        return status == PipelineStatus.SUCCEEDED;
    }

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }
}
