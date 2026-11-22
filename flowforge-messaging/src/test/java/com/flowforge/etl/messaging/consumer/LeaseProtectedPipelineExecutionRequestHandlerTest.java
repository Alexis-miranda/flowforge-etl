package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.PersistedStepExecution;
import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class LeaseProtectedPipelineExecutionRequestHandlerTest {
    private final ScheduledExecutorService renewalScheduler =
            Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stopRenewalScheduler() {
        renewalScheduler.shutdownNow();
    }


    private static final UUID EXECUTION_ID =
            UUID.fromString("7c5b2a5c-9c36-4b6e-a1d0-19b1d0b0f4d2");
    private static final PipelineExecutionRequest REQUEST =
            new PipelineExecutionRequest(
                    EXECUTION_ID, "cash", Map.of(), Instant.parse("2026-11-22T10:15:30Z"));
    private static final PipelineDefinition PIPELINE =
            new PipelineDefinition("cash-pipeline", "Cash", "", List.of());

    @Test
    void shouldExecuteUnderLeaseAndPersistStableExecutionId() {
        FakeExecutionRepository executions = new FakeExecutionRepository();
        FakeLeaseRepository leases = new FakeLeaseRepository();
        PipelineExecutor executor = new PipelineExecutor(List.of(), null, executions);
        LeaseProtectedPipelineExecutionRequestHandler handler = handler(
                request -> new PipelineExecutionRequestPipeline(PIPELINE, executor),
                leases, executions);

        handler.handle(REQUEST);

        PipelineExecutionSnapshot snapshot =
                executions.findSnapshotByExecutionId(EXECUTION_ID.toString()).orElseThrow();
        assertEquals(PIPELINE.id(), snapshot.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, snapshot.status());
        assertEquals(1, leases.acquireCalls);
        assertEquals(1, leases.releaseCalls);
    }

    @Test
    void shouldNotExecuteAnAlreadyRegisteredExecutionAgain() {
        FakeExecutionRepository executions = new FakeExecutionRepository();
        executions.snapshots.put(EXECUTION_ID.toString(), new PipelineExecutionSnapshot(
                EXECUTION_ID.toString(), PIPELINE.id(), PipelineStatus.SUCCEEDED,
                null, null, Instant.parse("2026-11-22T10:00:00Z"),
                Instant.parse("2026-11-22T10:01:00Z")));
        FakeLeaseRepository leases = new FakeLeaseRepository();
        PipelineExecutor executor = new PipelineExecutor(List.of(), null, executions);

        handler(request -> new PipelineExecutionRequestPipeline(PIPELINE, executor),
                leases, executions).handle(REQUEST);

        assertEquals(PipelineStatus.SUCCEEDED,
                executions.findSnapshotByExecutionId(EXECUTION_ID.toString()).orElseThrow().status());
        assertEquals(1, leases.releaseCalls);
    }

    @Test
    void shouldFailTransientlyWhenLeaseIsUnavailable() {
        FakeExecutionRepository executions = new FakeExecutionRepository();
        FakeLeaseRepository leases = new FakeLeaseRepository();
        leases.available = false;
        PipelineExecutor executor = new PipelineExecutor(List.of(), null, executions);

        assertThrows(ExecutionLeaseUnavailableException.class, () ->
                handler(request -> new PipelineExecutionRequestPipeline(PIPELINE, executor),
                        leases, executions).handle(REQUEST));
        assertEquals(0, leases.releaseCalls);
    }

    @Test
    void shouldRejectExecutionIdAlreadyUsedByAnotherPipeline() {
        FakeExecutionRepository executions = new FakeExecutionRepository();
        executions.snapshots.put(EXECUTION_ID.toString(), new PipelineExecutionSnapshot(
                EXECUTION_ID.toString(), "another-pipeline", PipelineStatus.SUCCEEDED,
                null, null, Instant.parse("2026-11-22T10:00:00Z"),
                Instant.parse("2026-11-22T10:01:00Z")));
        FakeLeaseRepository leases = new FakeLeaseRepository();
        PipelineExecutor executor = new PipelineExecutor(List.of(), null, executions);

        assertThrows(NonRetryablePipelineExecutionRequestException.class, () ->
                handler(request -> new PipelineExecutionRequestPipeline(PIPELINE, executor),
                        leases, executions).handle(REQUEST));
        assertEquals(1, leases.releaseCalls);
    }

    @Test
    void shouldRejectUnknownPipelineTypeAsNonRetryable() {
        FakeExecutionRepository executions = new FakeExecutionRepository();
        FakeLeaseRepository leases = new FakeLeaseRepository();

        assertThrows(NonRetryablePipelineExecutionRequestException.class, () ->
                handler(request -> {
                    throw new NonRetryablePipelineExecutionRequestException("unknown type");
                }, leases, executions).handle(REQUEST));
        assertEquals(0, leases.acquireCalls);
    }

    private static LeaseProtectedPipelineExecutionRequestHandler handler(
            PipelineExecutionRequestPipelineResolver resolver,
            FakeLeaseRepository leases,
            FakeExecutionRepository executions) {
        return new LeaseProtectedPipelineExecutionRequestHandler(
                resolver, leases, executions, "worker-test",
                Duration.ofMinutes(5), Duration.ofMinutes(1), renewalScheduler);
    }

    private static final class FakeLeaseRepository implements PipelineExecutionLeaseRepository {
        private boolean available = true;
        private int acquireCalls;
        private int releaseCalls;

        @Override
        public Optional<PipelineExecutionLease> tryAcquire(
                String executionId, String ownerId, Duration leaseDuration) {
            acquireCalls++;
            return available
                    ? Optional.of(new PipelineExecutionLease(
                            executionId, ownerId, Instant.now().plus(leaseDuration), 1))
                    : Optional.empty();
        }

        @Override
        public boolean renew(PipelineExecutionLease lease, Duration leaseDuration) {
            return true;
        }

        @Override
        public boolean release(PipelineExecutionLease lease) {
            releaseCalls++;
            return true;
        }
    }

    private static final class FakeExecutionRepository implements PipelineExecutionRepository {
        private final Map<String, PipelineExecutionSnapshot> snapshots = new java.util.HashMap<>();

        @Override
        public void start(PipelineExecutionSnapshot snapshot) {
            if (!tryStart(snapshot)) {
                throw new IllegalStateException("duplicate");
            }
        }

        @Override
        public boolean tryStart(PipelineExecutionSnapshot snapshot) {
            if (snapshots.containsKey(snapshot.executionId())) {
                return false;
            }
            snapshots.put(snapshot.executionId(), snapshot);
            return true;
        }

        @Override
        public void update(PipelineExecutionSnapshot snapshot) {
            snapshots.put(snapshot.executionId(), snapshot);
        }

        @Override
        public void save(PipelineExecutionResult result) {
            // The snapshot is the assertion target for these handler tests.
        }

        @Override
        public Optional<PipelineExecutionSnapshot> findSnapshotByExecutionId(String executionId) {
            return Optional.ofNullable(snapshots.get(executionId));
        }

        @Override
        public List<PipelineExecutionResult> findByPipelineId(String pipelineId) {
            return List.of();
        }

        @Override
        public Optional<PipelineExecutionResult> findByExecutionId(String executionId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(String pipelineId) {
            return snapshots.values().stream()
                    .filter(snapshot -> snapshot.pipelineId().equals(pipelineId))
                    .toList();
        }

        @Override
        public List<PersistedStepExecution> findStepExecutions(String executionId) {
            return List.of();
        }

        @Override
        public void startStep(String executionId, String stepId, String stepName, Instant startedAt) {
        }

        @Override
        public void finishStep(String executionId, StepExecutionRecord record) {
        }
    }
}
