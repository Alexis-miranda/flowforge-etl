package com.flowforge.etl.core.execution;

/** Conservative recommendation produced by recovery assessment. */
public enum StepRecoveryAction {
    NOT_STARTED,
    ALREADY_SUCCEEDED,
    ELIGIBLE_FOR_RETRY,
    RECONCILIATION_REQUIRED,
    MANUAL_INTERVENTION
}
