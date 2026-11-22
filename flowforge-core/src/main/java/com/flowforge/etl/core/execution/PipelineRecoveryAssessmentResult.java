package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.util.List;

/**
 * Read-only recovery assessment result.
 *
 * <p>The lease used to obtain this assessment has already been released.
 * This result is informational and does not authorize step execution.</p>
 */
public record PipelineRecoveryAssessmentResult(
        String executionId,
        String pipelineId,
        PipelineStatus observedExecutionStatus,
        RecoveryCoordinationStatus coordinationStatus,
        List<StepRecoveryAssessment> stepAssessments) {

    public PipelineRecoveryAssessmentResult {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("Pipeline ID must not be blank");
        }
        if (coordinationStatus == null) {
            throw new IllegalArgumentException("Coordination status must not be null");
        }
        if (stepAssessments == null) {
            throw new IllegalArgumentException("Step assessments must not be null");
        }
        stepAssessments = List.copyOf(stepAssessments);
        if (coordinationStatus == RecoveryCoordinationStatus.LEASE_UNAVAILABLE) {
            if (observedExecutionStatus != null || !stepAssessments.isEmpty()) {
                throw new IllegalArgumentException(
                        "Execution state must not be reported when the lease is unavailable");
            }
        } else if (observedExecutionStatus == null) {
            throw new IllegalArgumentException(
                    "Observed execution status is required after assessment");
        }
    }
}
