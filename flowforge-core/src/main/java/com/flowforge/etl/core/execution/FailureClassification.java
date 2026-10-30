package com.flowforge.etl.core.execution;

/**
 * Describes the operational handling expected for a failed pipeline step.
 *
 * <p>This classification is diagnostic metadata only. It does not trigger
 * retries or recovery automatically.</p>
 */
public enum FailureClassification {

    /** The failure may be transient and could be retried under a safe policy. */
    RETRYABLE,

    /** A human must inspect or correct the cause before continuing. */
    REQUIRES_HUMAN_INTERVENTION,

    /** The failed operation must not be retried automatically. */
    NON_RECOVERABLE,

    /** The failure has not been classified; recovery must not be assumed safe. */
    UNCLASSIFIED
}
