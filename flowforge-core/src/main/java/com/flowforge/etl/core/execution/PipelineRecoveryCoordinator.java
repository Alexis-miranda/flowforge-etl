package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coordinates a read-only recovery assessment under an execution lease.
 *
 * <p>The coordinator acquires ownership before reading the execution snapshot
 * and step history, then releases the lease before returning. It never retries,
 * resumes, or invokes pipeline steps. A returned assessment is informational
 * and is not an authorization to perform side effects.</p>
 */
public final class PipelineRecoveryCoordinator {

    private final PipelineExecutionRepository executionRepository;
    private final PipelineExecutionLeaseRepository leaseRepository;
    private final PipelineStepRecoveryEvaluator evaluator;

    public PipelineRecoveryCoordinator(
            PipelineExecutionRepository executionRepository,
            PipelineExecutionLeaseRepository leaseRepository,
            PipelineStepRecoveryEvaluator evaluator) {
        if (executionRepository == null) {
            throw new IllegalArgumentException("Execution repository must not be null");
        }
        if (leaseRepository == null) {
            throw new IllegalArgumentException("Lease repository must not be null");
        }
        if (evaluator == null) {
            throw new IllegalArgumentException("Recovery evaluator must not be null");
        }
        this.executionRepository = executionRepository;
        this.leaseRepository = leaseRepository;
        this.evaluator = evaluator;
    }

    /**
     * Acquires the execution lease, reloads durable state, and assesses steps.
     *
     * <p>Only FAILED executions and RUNNING executions whose previous lease
     * is no longer held can be assessed. The lease is always released before
     * a successful result is returned.</p>
     *
     * @return an assessment, or LEASE_UNAVAILABLE when another owner holds it
     */
    public PipelineRecoveryAssessmentResult assess(
            String executionId,
            String ownerId,
            Duration leaseDuration,
            PipelineDefinition pipeline,
            Map<String, StepRecoveryPolicy> policies) {
        requireText(executionId, "Execution ID");
        requireText(ownerId, "Lease owner ID");
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Lease duration must be positive");
        }
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline must not be null");
        }
        if (policies == null) {
            throw new IllegalArgumentException("Recovery policies must not be null");
        }

        Optional<PipelineExecutionLease> acquired = leaseRepository.tryAcquire(
                executionId, ownerId, leaseDuration);
        if (acquired.isEmpty()) {
            return new PipelineRecoveryAssessmentResult(
                    executionId,
                    pipeline.id(),
                    PipelineStatus.RUNNING,
                    RecoveryCoordinationStatus.LEASE_UNAVAILABLE,
                    List.of());
        }

        PipelineExecutionLease lease = acquired.orElseThrow();
        PipelineRecoveryAssessmentResult result;
        try {
            result = assessWhileLeased(executionId, pipeline, policies);
        } catch (RuntimeException | Error failure) {
            try {
                if (!leaseRepository.release(lease)) {
                    failure.addSuppressed(new IllegalStateException(
                            "Recovery lease was lost before assessment completed"));
                }
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }

        if (!leaseRepository.release(lease)) {
            throw new IllegalStateException(
                    "Recovery lease was lost before assessment completed");
        }
        return result;
    }

    private PipelineRecoveryAssessmentResult assessWhileLeased(
            String executionId,
            PipelineDefinition pipeline,
            Map<String, StepRecoveryPolicy> policies) {
        PipelineExecutionSnapshot snapshot = executionRepository
                .findSnapshotByExecutionId(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Execution does not exist: " + executionId));

        if (!snapshot.pipelineId().equals(pipeline.id())) {
            throw new IllegalArgumentException(
                    "Pipeline definition does not match the execution");
        }
        if (snapshot.status() != PipelineStatus.FAILED
                && snapshot.status() != PipelineStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only failed or interrupted executions can be assessed for recovery");
        }

        List<PersistedStepExecution> persistedSteps =
                executionRepository.findStepExecutions(executionId);
        List<StepRecoveryAssessment> assessments =
                evaluator.assess(pipeline, persistedSteps, policies);

        return new PipelineRecoveryAssessmentResult(
                executionId,
                pipeline.id(),
                snapshot.status(),
                RecoveryCoordinationStatus.ASSESSED,
                assessments);
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
