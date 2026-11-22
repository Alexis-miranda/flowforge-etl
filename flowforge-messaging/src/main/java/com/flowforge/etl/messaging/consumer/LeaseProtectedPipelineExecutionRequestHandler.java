package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.core.execution.DuplicatePipelineExecutionException;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Executes resolved requests under a SQL-backed execution lease.
 *
 * <p>The execution repository supplied here must be the same durable repository
 * used by each resolved PipelineExecutor. The repository's atomic execution-ID
 * claim is the final duplicate-execution guard.</p>
 *
 * <p>The handler periodically renews the lease while the pipeline runs. If a
 * renewal fails, execution is stopped at the next guard check. Long-running
 * step implementations should call the context guard before irreversible
 * side effects; fencing-token validation remains mandatory at persistence and
 * external side-effect boundaries.</p>
 *
 * <p>An existing execution with the same ID and pipeline is treated as already
 * claimed and is not executed again. Interrupted RUNNING executions are left
 * for the separate recovery workflow.</p>
 */
public final class LeaseProtectedPipelineExecutionRequestHandler
        implements PipelineExecutionRequestHandler {

    private final PipelineExecutionRequestPipelineResolver resolver;
    private final PipelineExecutionLeaseRepository leaseRepository;
    private final PipelineExecutionRepository executionRepository;
    private final String ownerId;
    private final Duration leaseDuration;
    private final Duration renewalInterval;
    private final ScheduledExecutorService renewalScheduler;

    public LeaseProtectedPipelineExecutionRequestHandler(
            PipelineExecutionRequestPipelineResolver resolver,
            PipelineExecutionLeaseRepository leaseRepository,
            PipelineExecutionRepository executionRepository,
            String ownerId,
            Duration leaseDuration,
            Duration renewalInterval,
            ScheduledExecutorService renewalScheduler) {
        this.resolver = Objects.requireNonNull(resolver, "Pipeline resolver must not be null");
        this.leaseRepository = Objects.requireNonNull(leaseRepository, "Lease repository must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository, "Execution repository must not be null");
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Worker owner ID must not be blank");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        this.ownerId = ownerId;
        if (renewalInterval == null || renewalInterval.isZero()
                || renewalInterval.isNegative()
                || renewalInterval.compareTo(leaseDuration) >= 0) {
            throw new IllegalArgumentException(
                    "Renewal interval must be positive and shorter than the lease duration");
        }
        this.leaseDuration = leaseDuration;
        this.renewalInterval = renewalInterval;
        this.renewalScheduler = Objects.requireNonNull(
                renewalScheduler, "Lease renewal scheduler must not be null");
    }

    @Override
    public void handle(PipelineExecutionRequest request) {
        Objects.requireNonNull(request, "Request must not be null");
        PipelineExecutionRequestPipeline resolved = resolver.resolve(request);
        if (resolved == null) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "No pipeline is registered for type: " + request.pipelineType());
        }

        String executionId = request.executionId().toString();
        String pipelineId = resolved.definition().id();
        Optional<PipelineExecutionLease> acquired = leaseRepository.tryAcquire(
                executionId, ownerId, leaseDuration);
        if (acquired.isEmpty()) {
            throw new ExecutionLeaseUnavailableException(executionId);
        }

        PipelineExecutionLease lease = acquired.orElseThrow();
        RuntimeException processingFailure = null;
        try {
            try (RenewingPipelineExecutionLeaseGuard leaseGuard =
                         new RenewingPipelineExecutionLeaseGuard(
                                 leaseRepository, lease, leaseDuration,
                                 renewalInterval, renewalScheduler)) {
                Optional<PipelineExecutionSnapshot> existing =
                        executionRepository.findSnapshotByExecutionId(executionId);
                if (existing.isPresent()) {
                    verifyExistingExecution(existing.orElseThrow(), pipelineId, executionId);
                    return;
                }

                try {
                    resolved.executor().executeWithLease(
                            resolved.definition(), lease, leaseGuard);
                } catch (DuplicatePipelineExecutionException duplicate) {
                    PipelineExecutionSnapshot snapshot = executionRepository
                            .findSnapshotByExecutionId(executionId)
                            .orElseThrow(() -> duplicate);
                    verifyExistingExecution(snapshot, pipelineId, executionId);
                }
            }
        } catch (RuntimeException failure) {
            processingFailure = failure;
            throw failure;
        } finally {
            try {
                if (!leaseRepository.release(lease)) {
                    IllegalStateException lostLease = new IllegalStateException(
                            "Execution lease was lost before request handling completed: "
                                    + executionId);
                    if (processingFailure != null) {
                        processingFailure.addSuppressed(lostLease);
                    } else {
                        throw lostLease;
                    }
                }
            } catch (RuntimeException releaseFailure) {
                if (processingFailure != null) {
                    processingFailure.addSuppressed(releaseFailure);
                } else {
                    throw releaseFailure;
                }
            }
        }
    }

    private static void verifyExistingExecution(
            PipelineExecutionSnapshot snapshot,
            String expectedPipelineId,
            String executionId) {
        if (!snapshot.pipelineId().equals(expectedPipelineId)) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Execution ID is already registered for a different pipeline: "
                            + executionId);
        }
    }
}
