package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.PipelineExecutionLeaseGuard;
import com.flowforge.etl.core.execution.PipelineExecutionLeaseLostException;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renews one execution lease periodically and fails closed when renewal fails.
 *
 * <p>The scheduler is owned by the caller. A renewal error is treated as loss
 * of ownership because the worker cannot safely assume that its lease remains
 * current when the database cannot confirm renewal.</p>
 */
public final class RenewingPipelineExecutionLeaseGuard
        implements PipelineExecutionLeaseGuard, AutoCloseable {

    private final String executionId;
    private final AtomicReference<Throwable> renewalFailure = new AtomicReference<>();
    private final ScheduledFuture<?> renewalTask;

    public RenewingPipelineExecutionLeaseGuard(
            PipelineExecutionLeaseRepository repository,
            PipelineExecutionLease lease,
            Duration leaseDuration,
            Duration renewalInterval,
            ScheduledExecutorService scheduler) {
        Objects.requireNonNull(repository, "Lease repository must not be null");
        Objects.requireNonNull(lease, "Execution lease must not be null");
        Objects.requireNonNull(scheduler, "Lease renewal scheduler must not be null");
        this.executionId = lease.executionId();

        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        if (renewalInterval == null || renewalInterval.isZero() || renewalInterval.isNegative()) {
            throw new IllegalArgumentException("Renewal interval must be positive");
        }
        if (renewalInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException(
                    "Renewal interval must be shorter than the lease duration");
        }

        long intervalNanos = renewalInterval.toNanos();
        this.renewalTask = scheduler.scheduleAtFixedRate(() -> {
            if (renewalFailure.get() != null) {
                return;
            }
            try {
                if (!repository.renew(lease, leaseDuration)) {
                    renewalFailure.compareAndSet(null,
                            new IllegalStateException("Lease repository rejected renewal"));
                }
            } catch (RuntimeException failure) {
                renewalFailure.compareAndSet(null, failure);
            }
        }, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void assertLeaseValid() {
        Throwable failure = renewalFailure.get();
        if (failure != null) {
            throw new PipelineExecutionLeaseLostException(executionId, failure);
        }
    }

    @Override
    public void close() {
        renewalTask.cancel(false);
    }
}
