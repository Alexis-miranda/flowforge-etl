package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Coordinates time-bounded ownership of pipeline executions.
 *
 * <p>Implementations must make acquisition, renewal and release atomic with
 * respect to competing workers. Fencing tokens must increase whenever an
 * expired or released lease is acquired again and must never be reset for an
 * execution ID.</p>
 */
public interface PipelineExecutionLeaseRepository {

    /**
     * Attempts to acquire an unowned or expired execution lease.
     *
     * @return the new lease, or empty when another worker currently owns it
     */
    Optional<PipelineExecutionLease> tryAcquire(
            String executionId,
            String ownerId,
            Instant now,
            Duration leaseDuration);

    /**
     * Extends a lease only if its owner and fencing token are still current
     * and the lease has not expired.
     */
    boolean renew(
            PipelineExecutionLease lease,
            Instant now,
            Duration leaseDuration);

    /**
     * Releases a lease only if its owner and fencing token are still current.
     * The fencing-token history is retained after release.
     */
    boolean release(PipelineExecutionLease lease, Instant releasedAt);
}
