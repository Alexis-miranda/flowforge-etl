package com.flowforge.etl.core.execution;

/** Assessment for one step; it is advisory and does not perform recovery. */
public record StepRecoveryAssessment(
        String stepId,
        PersistedStepExecutionStatus observedStatus,
        StepRecoveryPolicy policy,
        StepRecoveryAction action,
        String reason) {

    public StepRecoveryAssessment {
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("Step ID must not be blank");
        }
        if (observedStatus == null || policy == null || action == null) {
            throw new IllegalArgumentException("Recovery assessment fields must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Assessment reason must not be blank");
        }
    }
}
