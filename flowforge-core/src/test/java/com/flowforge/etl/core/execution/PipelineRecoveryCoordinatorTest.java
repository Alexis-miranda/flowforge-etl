package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.repository.InMemoryPipelineExecutionLeaseRepository;
import com.flowforge.etl.core.repository.InMemoryPipelineExecutionRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineRecoveryCoordinatorTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(1);

    private final PipelineDefinition pipeline = new PipelineDefinition(
            "cash", "Cash pipeline", "",
            List.of(
                    new PipelineStep("extract", "Extract", StepType.EXTRACT, 1),
                    new PipelineStep("publish", "Publish", StepType.LOAD, 2)));

    @Test
    void shouldAcquireLeaseAssessPersistedFailureAndReleaseLease() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository leases =
                new InMemoryPipelineExecutionLeaseRepository();
        startFailedExecution(executions, "execution-1", pipeline.id());
        executions.startStep("execution-1", "extract", "Extract", STARTED_AT);
        executions.finishStep("execution-1", new StepExecutionRecord(
                "extract", "Extract", StepExecutionStatus.FAILED,
                STARTED_AT, STARTED_AT.plusSeconds(2),
                "java.io.IOException", "temporary I/O failure",
                null, null, null, FailureClassification.RETRYABLE));

        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        PipelineRecoveryAssessmentResult result = coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of("extract", StepRecoveryPolicy.IDEMPOTENT));

        assertEquals(RecoveryCoordinationStatus.ASSESSED, result.coordinationStatus());
        assertEquals(PipelineStatus.FAILED, result.observedExecutionStatus());
        assertEquals(List.of(
                StepRecoveryAction.ELIGIBLE_FOR_RETRY,
                StepRecoveryAction.NOT_STARTED),
                result.stepAssessments().stream().map(StepRecoveryAssessment::action).toList());

        PipelineExecutionLease reacquired = leases.tryAcquire(
                "execution-1", "next-worker", LEASE_DURATION).orElseThrow();
        assertTrue(leases.release(reacquired));
    }

    @Test
    void shouldReturnLeaseUnavailableWithoutReadingOrReportingExecutionState() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository leases =
                new InMemoryPipelineExecutionLeaseRepository();
        PipelineExecutionLease existing = leases.tryAcquire(
                "execution-1", "active-worker", LEASE_DURATION).orElseThrow();

        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        PipelineRecoveryAssessmentResult result = coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of());

        assertEquals(RecoveryCoordinationStatus.LEASE_UNAVAILABLE,
                result.coordinationStatus());
        assertNull(result.observedExecutionStatus());
        assertTrue(result.stepAssessments().isEmpty());
        assertTrue(leases.release(existing));
    }

    @Test
    void shouldTreatRunningStepAsReconciliationRequiredForInterruptedExecution() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository leases =
                new InMemoryPipelineExecutionLeaseRepository();
        executions.start(runningSnapshot("execution-1", pipeline.id()));
        executions.startStep("execution-1", "extract", "Extract", STARTED_AT);

        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        PipelineRecoveryAssessmentResult result = coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of("extract", StepRecoveryPolicy.IDEMPOTENT));

        assertEquals(PipelineStatus.RUNNING, result.observedExecutionStatus());
        assertEquals(StepRecoveryAction.RECONCILIATION_REQUIRED,
                result.stepAssessments().get(0).action());
    }

    @Test
    void shouldRejectSuccessfulExecutionAndReleaseLease() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository leases =
                new InMemoryPipelineExecutionLeaseRepository();
        executions.start(runningSnapshot("execution-1", pipeline.id()));
        executions.update(new PipelineExecutionSnapshot(
                "execution-1", pipeline.id(), PipelineStatus.SUCCEEDED,
                null, null, STARTED_AT, STARTED_AT.plusSeconds(5)));

        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        assertThrows(IllegalStateException.class, () -> coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of()));

        PipelineExecutionLease reacquired = leases.tryAcquire(
                "execution-1", "next-worker", LEASE_DURATION).orElseThrow();
        assertTrue(leases.release(reacquired));
    }

    @Test
    void shouldRejectPipelineDefinitionThatDoesNotMatchExecutionAndReleaseLease() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository leases =
                new InMemoryPipelineExecutionLeaseRepository();
        startFailedExecution(executions, "execution-1", "different-pipeline");

        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        assertThrows(IllegalArgumentException.class, () -> coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of()));

        PipelineExecutionLease reacquired = leases.tryAcquire(
                "execution-1", "next-worker", LEASE_DURATION).orElseThrow();
        assertTrue(leases.release(reacquired));
    }

    @Test
    void shouldFailClosedWhenLeaseIsLostBeforeAssessmentReturns() {
        InMemoryPipelineExecutionRepository executions =
                new InMemoryPipelineExecutionRepository();
        InMemoryPipelineExecutionLeaseRepository delegate =
                new InMemoryPipelineExecutionLeaseRepository();
        startFailedExecution(executions, "execution-1", pipeline.id());

        PipelineExecutionLeaseRepositoryThatLosesRelease leases =
                new PipelineExecutionLeaseRepositoryThatLosesRelease(delegate);
        PipelineRecoveryCoordinator coordinator =
                new PipelineRecoveryCoordinator(executions, leases,
                        new PipelineStepRecoveryEvaluator());

        assertThrows(IllegalStateException.class, () -> coordinator.assess(
                "execution-1", "recovery-worker", LEASE_DURATION,
                pipeline, Map.of()));
    }

    private static void startFailedExecution(
            InMemoryPipelineExecutionRepository repository,
            String executionId,
            String pipelineId) {
        repository.start(runningSnapshot(executionId, pipelineId));
        repository.update(new PipelineExecutionSnapshot(
                executionId, pipelineId, PipelineStatus.FAILED,
                "extract", "execution failed",
                STARTED_AT, STARTED_AT.plusSeconds(5)));
    }

    private static PipelineExecutionSnapshot runningSnapshot(
            String executionId, String pipelineId) {
        return new PipelineExecutionSnapshot(
                executionId, pipelineId, PipelineStatus.RUNNING,
                null, null, STARTED_AT, null);
    }

    private static final class PipelineExecutionLeaseRepositoryThatLosesRelease
            implements com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository {
        private final com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository delegate;

        private PipelineExecutionLeaseRepositoryThatLosesRelease(
                com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Optional<PipelineExecutionLease> tryAcquire(
                String executionId, String ownerId, Duration leaseDuration) {
            return delegate.tryAcquire(executionId, ownerId, leaseDuration);
        }

        @Override
        public boolean renew(PipelineExecutionLease lease, Duration leaseDuration) {
            return delegate.renew(lease, leaseDuration);
        }

        @Override
        public boolean release(PipelineExecutionLease lease) {
            return false;
        }
    }
}
