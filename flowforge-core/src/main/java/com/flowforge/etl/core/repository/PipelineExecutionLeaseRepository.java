package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.time.Duration;
import java.util.Optional;

/**
 * Coordinates time-bounded ownership of pipeline executions.
 *
 * <p>Implementations use their own authoritative clock. A distributed
 * implementation should use the database clock rather than worker clocks.
 * Fencing tokens must increase whenever a lease is acquired again and must
 * never be reset for an execution ID.</p>
 */
public interface PipelineExecutionLeaseRepository {

    /** Attempts to acquire an unowned or expired execution lease. */
    Optional<PipelineExecutionLease> tryAcquire(
            String executionId, String ownerId, Duration leaseDuration);

    /** Extends a lease only while its owner and fencing token are current. */
    boolean renew(PipelineExecutionLease lease, Duration leaseDuration);

    /**
     * Releases a lease only if its owner and fencing token are still current.
     * The fencing-token history is retained after release.
     */
    boolean release(PipelineExecutionLease lease);
}
