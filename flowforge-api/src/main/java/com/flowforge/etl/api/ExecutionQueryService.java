package com.flowforge.etl.api;

import com.flowforge.etl.core.execution.PersistedStepExecution;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestStatus;

import java.time.Instant;
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
            return Optional.of(new ExecutionStatus(
                    executionId.toString(),
                    value.pipelineId(),
                    value.status().name(),
                    value.failedStepId(),
                    value.errorMessage(),
                    value.startedAt(),
                    value.finishedAt()));
        }

        return requests.findRequestStatus(executionId)
                .map(ExecutionQueryService::queuedStatus);
    }

    public Optional<ExecutionSteps> findSteps(UUID executionId) {
        return findStatus(executionId).map(status -> new ExecutionSteps(
                status.executionId(),
                status.status(),
                executions.findStepExecutions(executionId.toString())));
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
                null);
    }

    public record ExecutionStatus(
            String executionId,
            String pipelineId,
            String status,
            String failedStepId,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) { }

    public record ExecutionSteps(
            String executionId,
            String status,
            List<PersistedStepExecution> steps) {
        public ExecutionSteps {
            steps = List.copyOf(steps);
        }
    }
}
