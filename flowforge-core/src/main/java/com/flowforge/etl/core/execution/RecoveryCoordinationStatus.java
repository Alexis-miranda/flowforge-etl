package com.flowforge.etl.core.execution;

/** Outcome of attempting to coordinate a read-only recovery assessment. */
public enum RecoveryCoordinationStatus {
    /** The execution lease was acquired and the persisted state was assessed. */
    ASSESSED,
    /** Another worker currently owns the execution lease. */
    LEASE_UNAVAILABLE
}
