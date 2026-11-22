package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory lease repository for tests and single-process execution. */
public final class InMemoryPipelineExecutionLeaseRepository
        implements PipelineExecutionLeaseRepository {

    private final Map<String, LeaseState> leases = new HashMap<>();

    @Override
    public synchronized Optional<PipelineExecutionLease> tryAcquire(
            String executionId,
            String ownerId,
            Instant now,
            Duration leaseDuration) {
        validateRequest(executionId, ownerId, now, leaseDuration);

        LeaseState existing = leases.get(executionId);
        if (existing != null && existing.ownerId() != null
                && existing.leaseUntil().isAfter(now)) {
            return Optional.empty();
        }

        long nextToken = existing == null ? 1 : existing.fencingToken() + 1;
        Instant leaseUntil = now.plus(leaseDuration);
        leases.put(executionId,
                new LeaseState(ownerId, leaseUntil, nextToken));

        return Optional.of(new PipelineExecutionLease(
                executionId, ownerId, leaseUntil, nextToken));
    }

    @Override
    public synchronized boolean renew(
            PipelineExecutionLease lease,
            Instant now,
            Duration leaseDuration) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
        validateTimeAndDuration(now, leaseDuration);

        LeaseState current = leases.get(lease.executionId());
        if (!matches(current, lease) || !current.leaseUntil().isAfter(now)) {
            return false;
        }

        Instant renewedUntil = now.plus(leaseDuration);
        leases.put(lease.executionId(), new LeaseState(
                current.ownerId(), renewedUntil, current.fencingToken()));
        return true;
    }

    @Override
    public synchronized boolean release(
            PipelineExecutionLease lease,
            Instant releasedAt) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
        if (releasedAt == null) {
            throw new IllegalArgumentException("Release time must not be null");
        }

        LeaseState current = leases.get(lease.executionId());
        if (!matches(current, lease)) {
            return false;
        }

        // Keep the row/token so a later acquisition cannot reuse this token.
        leases.put(lease.executionId(), new LeaseState(
                null, releasedAt, current.fencingToken()));
        return true;
    }

    private static boolean matches(
            LeaseState state,
            PipelineExecutionLease lease) {
        return state != null
                && lease.ownerId().equals(state.ownerId())
                && lease.fencingToken() == state.fencingToken();
    }

    private static void validateRequest(
            String executionId,
            String ownerId,
            Instant now,
            Duration leaseDuration) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Lease owner ID must not be blank");
        }
        validateTimeAndDuration(now, leaseDuration);
    }

    private static void validateTimeAndDuration(
            Instant now,
            Duration leaseDuration) {
        if (now == null) {
            throw new IllegalArgumentException("Current time must not be null");
        }
        if (leaseDuration == null || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Lease duration must be positive");
        }
        try {
            now.plus(leaseDuration);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Lease duration is too large", exception);
        }
    }

    private record LeaseState(
            String ownerId,
            Instant leaseUntil,
            long fencingToken) {
    }
}
