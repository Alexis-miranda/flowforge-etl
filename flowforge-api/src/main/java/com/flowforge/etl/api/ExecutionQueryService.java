package com.flowforge.etl.api;

import com.flowforge.etl.core.execution.PersistedStepExecution;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads execution lifecycle state from SQL-backed execution and request repositories. */
public final class ExecutionQueryService {

    private final PipelineExecutionRepository executions;
    private final PipelineExecutionRequestOutbox requests;

    public ExecutionQueryService(
            PipelineExecutionRepository executions,
            PipelineExecutionRequestOutbox requests) {
        this.executions = Objects.requireNonNull(executions, "Execution repository must not be null");
        this.requests = Objects.requireNonNull(requests, "Request outbox must not be null");
    }

    public Optional<ExecutionStatus> findStatus(UUID executionId) {
        Objects.requireNonNull(executionId, "Execution ID must not be null");
        Optional<PipelineExecutionSnapshot> snapshot =
                executions.findSnapshotByExecutionId(executionId.toString());
        if (snapshot.isPresent()) {
            PipelineExecutionSnapshot value = snapshot.get();
            Instant requestedAt = requests.findRequestStatus(executionId)
                    .map(PipelineExecutionRequestStatus::requestedAt)
                    .orElse(null);
            return Optional.of(toStatus(executionId, value, requestedAt));
        }

        return requests.findRequestStatus(executionId)
                .map(ExecutionQueryService::queuedStatus);
    }

    /**
     * Returns the latest accepted requests, including requests still waiting
     * for RabbitMQ dispatch or worker execution.
     */
    public List<ExecutionStatus> findRecent(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }

        List<ExecutionStatus> recent = new ArrayList<>();
        for (PipelineExecutionRequestStatus request : requests.findRecentRequests(limit)) {
            Optional<PipelineExecutionSnapshot> snapshot =
                    executions.findSnapshotByExecutionId(request.executionId().toString());
            recent.add(snapshot
                    .map(value -> toStatus(request.executionId(), value, request.requestedAt()))
                    .orElseGet(() -> queuedStatus(request)));
        }
        return List.copyOf(recent);
    }

    public Optional<ExecutionSteps> findSteps(UUID executionId) {
        return findStatus(executionId).map(status -> new ExecutionSteps(
                status.executionId(),
                status.status(),
                executions.findStepExecutions(executionId.toString())));
    }

    private static ExecutionStatus toStatus(
            UUID executionId,
            PipelineExecutionSnapshot snapshot,
            Instant requestedAt) {
        return new ExecutionStatus(
                executionId.toString(),
                snapshot.pipelineId(),
                snapshot.status().name(),
                snapshot.failedStepId(),
                snapshot.errorMessage(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                requestedAt);
    }

    private static ExecutionStatus queuedStatus(PipelineExecutionRequestStatus request) {
        String status = "PENDING".equals(request.deliveryStatus()) ? "QUEUED" : "DISPATCHED";
        return new ExecutionStatus(
                request.executionId().toString(),
                request.pipelineType(),
                status,
                null,
                request.lastError(),
                null,
                null,
                request.requestedAt());
    }

    public record ExecutionStatus(
            String executionId,
            String pipelineId,
            String status,
            String failedStepId,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant requestedAt) { }

    public record ExecutionSteps(
            String executionId,
            String status,
            List<PersistedStepExecution> steps) {
        public ExecutionSteps {
            steps = List.copyOf(steps);
        }
    }
}
