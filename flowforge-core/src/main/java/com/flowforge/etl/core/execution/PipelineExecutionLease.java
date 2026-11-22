package com.flowforge.etl.core.execution;

import java.time.Instant;

/**
 * A time-bounded ownership grant for processing one pipeline execution.
 *
 * @param executionId execution being protected
 * @param ownerId stable identifier for the worker holding the lease
 * @param leaseUntil instant at which the lease expires
 * @param fencingToken monotonically increasing ownership generation
 */
public record PipelineExecutionLease(
        String executionId,
        String ownerId,
        Instant leaseUntil,
        long fencingToken) {

    public PipelineExecutionLease {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Lease owner ID must not be blank");
        }
        if (leaseUntil == null) {
            throw new IllegalArgumentException("Lease expiration must not be null");
        }
        if (fencingToken < 1) {
            throw new IllegalArgumentException("Fencing token must be positive");
        }
    }

    /** Returns whether this lease has expired at the supplied instant. */
    public boolean isExpiredAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Current time must not be null");
        }
        return !leaseUntil.isAfter(instant);
    }
}
