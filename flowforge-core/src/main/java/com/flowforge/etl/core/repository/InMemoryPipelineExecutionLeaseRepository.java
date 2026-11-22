package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory lease repository for tests and single-process execution. */
public final class InMemoryPipelineExecutionLeaseRepository
        implements PipelineExecutionLeaseRepository {

    private final Clock clock;
    private final Map<String, LeaseState> leases = new HashMap<>();

    public InMemoryPipelineExecutionLeaseRepository() {
        this(Clock.systemUTC());
    }

    public InMemoryPipelineExecutionLeaseRepository(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }
        this.clock = clock;
    }

    @Override
    public synchronized Optional<PipelineExecutionLease> tryAcquire(
            String executionId, String ownerId, Duration leaseDuration) {
        validateRequest(executionId, ownerId, leaseDuration);
        Instant now = clock.instant();
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
            PipelineExecutionLease lease, Duration leaseDuration) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
        validateDuration(leaseDuration);
        Instant now = clock.instant();
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
    public synchronized boolean release(PipelineExecutionLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("Lease must not be null");
        }
        LeaseState current = leases.get(lease.executionId());
        if (!matches(current, lease)) {
            return false;
        }
        leases.put(lease.executionId(), new LeaseState(
                null, clock.instant(), current.fencingToken()));
        return true;
    }

    private static boolean matches(LeaseState state, PipelineExecutionLease lease) {
        return state != null
                && lease.ownerId().equals(state.ownerId())
                && lease.fencingToken() == state.fencingToken();
    }

    private static void validateRequest(
            String executionId, String ownerId, Duration leaseDuration) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Lease owner ID must not be blank");
        }
        validateDuration(leaseDuration);
    }

    private static void validateDuration(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero()
                || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        try {
            Instant.now().plus(leaseDuration);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Lease duration is too large", exception);
        }
    }

    private record LeaseState(String ownerId, Instant leaseUntil, long fencingToken) {
    }
}
