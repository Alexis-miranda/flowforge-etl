package com.flowforge.etl.core.execution;

/**
 * Declares the recovery characteristics of a pipeline step.
 *
 * <p>This is a policy input for assessment only; it never starts a retry.</p>
 */
public enum StepRecoveryPolicy {
    /** Repeating the step is designed to produce the same final state. */
    IDEMPOTENT,
    /** The external effect must be inspected before deciding whether to retry. */
    RECONCILIATION_REQUIRED,
    /** Retrying requires an explicit operator decision. */
    MANUAL_ONLY,
    /** No recovery guarantee has been declared. */
    UNCLASSIFIED
}
