package com.flowforge.etl.api;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PersistedStepExecution;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestStatus;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionQueryServiceTest {

    @Test
    void shouldReportQueuedBeforeWorkerCreatesExecutionSnapshot() {
        UUID id = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-11-22T10:00:00Z");
        PipelineExecutionRequestStatus requestStatus = new PipelineExecutionRequestStatus(
                id, "cash", "PENDING", requestedAt, 0, null);
        ExecutionQueryService service = new ExecutionQueryService(
                new EmptyExecutionRepository(), new StatusOutbox(requestStatus));

        var status = service.findStatus(id).orElseThrow();
        var steps = service.findSteps(id).orElseThrow();

        assertEquals("QUEUED", status.status());
        assertEquals(id.toString(), status.executionId());
        assertEquals("QUEUED", steps.status());
        assertTrue(steps.steps().isEmpty());
    }

    @Test
    void shouldReportDispatchedWhenBrokerAcceptedRequestButWorkerHasNotStarted() {
        UUID id = UUID.randomUUID();
        PipelineExecutionRequestStatus requestStatus = new PipelineExecutionRequestStatus(
                id, "cash", "PUBLISHED",
                Instant.parse("2026-11-22T10:00:00Z"), 0, null);
        ExecutionQueryService service = new ExecutionQueryService(
                new EmptyExecutionRepository(), new StatusOutbox(requestStatus));

        assertEquals("DISPATCHED", service.findStatus(id).orElseThrow().status());
    }

    @Test
    void shouldReturnEmptyForUnknownExecution() {
        ExecutionQueryService service = new ExecutionQueryService(
                new EmptyExecutionRepository(), new StatusOutbox(null));

        assertTrue(service.findStatus(UUID.randomUUID()).isEmpty());
    }

    private static final class StatusOutbox implements PipelineExecutionRequestOutbox {
        private final PipelineExecutionRequestStatus status;

        private StatusOutbox(PipelineExecutionRequestStatus status) {
            this.status = status;
        }

        @Override
        public void enqueue(PipelineExecutionRequest request) { }

        @Override
        public List<PipelineExecutionOutboxMessage> findPending(int batchSize) {
            return List.of();
        }

        @Override
        public boolean markPublished(UUID messageId, Instant publishedAt) {
            return false;
        }

        @Override
        public boolean recordPublishFailure(UUID messageId, String errorMessage) {
            return false;
        }

        @Override
        public Optional<PipelineExecutionRequestStatus> findRequestStatus(UUID executionId) {
            return Optional.ofNullable(status)
                    .filter(value -> value.executionId().equals(executionId));
        }
    }

    private static final class EmptyExecutionRepository implements PipelineExecutionRepository {
        @Override
        public void start(PipelineExecutionSnapshot snapshot) { }

        @Override
        public void update(PipelineExecutionSnapshot snapshot) { }

        @Override
        public void save(PipelineExecutionResult result) { }

        @Override
        public Optional<PipelineExecutionSnapshot> findSnapshotByExecutionId(String executionId) {
            return Optional.empty();
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
            return List.of();
        }
    }
}
