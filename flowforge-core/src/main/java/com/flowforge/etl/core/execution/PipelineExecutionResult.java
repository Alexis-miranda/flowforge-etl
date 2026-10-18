
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

/**
 * Represents the result of a pipeline execution.
 *
 * @param pipelineId the identifier of the executed pipeline
 * @param status the final execution status
 * @param failedStepId the identifier of the failed step, if any
 * @param errorMessage the error message, if any
 */
public record PipelineExecutionResult(
        String pipelineId,
        PipelineStatus status,
        String failedStepId,
        String errorMessage) {

    /**
     * Validates the execution result.
     */
    public PipelineExecutionResult {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be blank");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "Pipeline status must not be null");
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
}
