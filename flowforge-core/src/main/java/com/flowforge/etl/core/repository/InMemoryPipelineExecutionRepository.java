
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.execution.PersistedStepExecution;
import com.flowforge.etl.core.execution.PersistedStepExecutionStatus;
import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.execution.StepExecutionStatus;

import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores pipeline execution snapshots and final results in memory.
 *
 * <p>Data is lost when the application stops.</p>
 */
public class InMemoryPipelineExecutionRepository
        implements PipelineExecutionRepository {

    private final Map<String, PipelineExecutionSnapshot> snapshots =
            new LinkedHashMap<>();

    private final Map<String, PipelineExecutionResult> results =
            new LinkedHashMap<>();

    private final Map<String, Map<String, PersistedStepExecution>> stepExecutions =
            new LinkedHashMap<>();

    @Override
    public synchronized void start(PipelineExecutionSnapshot snapshot) {
        if (!tryStart(snapshot)) {
            throw new IllegalArgumentException(
                    "Execution snapshot already exists: "
                            + snapshot.executionId());
        }
    }

    @Override
    public synchronized boolean tryStart(PipelineExecutionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Execution snapshot must not be null");
        }

        if (snapshot.status()
                != com.flowforge.etl.core.domain.PipelineStatus.RUNNING) {
            throw new IllegalArgumentException(
                    "Initial execution snapshot must have RUNNING status");
        }

        if (snapshots.containsKey(snapshot.executionId())) {
            return false;
        }

        snapshots.put(snapshot.executionId(), snapshot);
        return true;
    }

    @Override
    public synchronized void update(PipelineExecutionSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "Execution snapshot must not be null");
        }

        PipelineExecutionSnapshot existing =
                snapshots.get(snapshot.executionId());

        if (existing == null) {
            throw new IllegalArgumentException(
                    "Execution snapshot does not exist: "
                            + snapshot.executionId());
        }

        if (!existing.pipelineId().equals(snapshot.pipelineId())) {
            throw new IllegalArgumentException(
                    "Pipeline ID cannot change during execution");
        }

        if (existing.finished()) {
            throw new IllegalStateException(
                    "Pipeline execution has already finished");
        }

        snapshots.put(snapshot.executionId(), snapshot);
    }

    @Override
    public synchronized void save(PipelineExecutionResult result) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "Execution result must not be null");
        }

        if (results.containsKey(result.executionId())) {
            throw new IllegalArgumentException(
                    "Execution result already exists: "
                            + result.executionId());
        }

        results.put(result.executionId(), result);
    }

    @Override
    public synchronized Optional<PipelineExecutionSnapshot>
    findSnapshotByExecutionId(String executionId) {
        validateExecutionId(executionId);
        return Optional.ofNullable(snapshots.get(executionId));
    }

    @Override
    public synchronized Optional<PipelineExecutionResult>
    findByExecutionId(String executionId) {
        validateExecutionId(executionId);
        return Optional.ofNullable(results.get(executionId));
    }

    @Override
    public synchronized List<PipelineExecutionSnapshot>
    findSnapshotsByPipelineId(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be null or blank");
        }

        List<PipelineExecutionSnapshot> matching = new ArrayList<>();

        for (PipelineExecutionSnapshot snapshot : snapshots.values()) {
            if (snapshot.pipelineId().equals(pipelineId)) {
                matching.add(snapshot);
            }
        }

        matching.sort(
                java.util.Comparator.comparing(
                        PipelineExecutionSnapshot::startedAt
                ).reversed()
        );

        return List.copyOf(matching);
    }

    @Override
    public synchronized List<PipelineExecutionResult> findByPipelineId(
            String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be null or blank");
        }

        List<PipelineExecutionResult> matching = new ArrayList<>();

        for (PipelineExecutionResult result : results.values()) {
            if (result.pipelineId().equals(pipelineId)) {
                matching.add(result);
            }
        }

        return List.copyOf(matching);
    }


    @Override
    public synchronized void startStep(
            String executionId, String stepId, String stepName, Instant startedAt) {
        validateExecutionId(executionId);
        if (stepId == null || stepId.isBlank()) {
            throw new IllegalArgumentException("Step ID must not be blank");
        }
        if (stepName == null || stepName.isBlank()) {
            throw new IllegalArgumentException("Step name must not be blank");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("Step start timestamp must not be null");
        }
        if (!snapshots.containsKey(executionId)) {
            throw new IllegalArgumentException("Execution does not exist: " + executionId);
        }
        Map<String, PersistedStepExecution> steps =
                stepExecutions.computeIfAbsent(executionId, ignored -> new LinkedHashMap<>());
        if (steps.containsKey(stepId)) {
            throw new IllegalStateException("Step execution already exists: " + stepId);
        }
        steps.put(stepId, new PersistedStepExecution(
                stepId, PersistedStepExecutionStatus.RUNNING, startedAt, null, null));
    }

    @Override
    public synchronized void finishStep(String executionId, StepExecutionRecord record) {
        validateExecutionId(executionId);
        if (record == null) {
            throw new IllegalArgumentException("Step execution record must not be null");
        }
        Map<String, PersistedStepExecution> steps = stepExecutions.get(executionId);
        if (steps == null) {
            throw new IllegalStateException("Step execution was not started: " + record.stepId());
        }
        if (record.status() == StepExecutionStatus.SKIPPED) {
            if (steps.containsKey(record.stepId())) {
                throw new IllegalStateException("Skipped step already has a record: " + record.stepId());
            }
            steps.put(record.stepId(), new PersistedStepExecution(
                    record.stepId(), PersistedStepExecutionStatus.SKIPPED,
                    null, record.finishedAt(), null));
            return;
        }
        PersistedStepExecution current = steps.get(record.stepId());
        if (current == null || current.status() != PersistedStepExecutionStatus.RUNNING) {
            throw new IllegalStateException("Running step record was not found: " + record.stepId());
        }
        PersistedStepExecutionStatus status = record.status() == StepExecutionStatus.SUCCEEDED
                ? PersistedStepExecutionStatus.SUCCEEDED : PersistedStepExecutionStatus.FAILED;
        steps.put(record.stepId(), new PersistedStepExecution(
                record.stepId(), status, current.startedAt(), record.finishedAt(),
                record.failureClassification()));
    }

    @Override
    public synchronized List<PersistedStepExecution> findStepExecutions(String executionId) {
        validateExecutionId(executionId);
        Map<String, PersistedStepExecution> steps = stepExecutions.get(executionId);
        return steps == null ? List.of() : List.copyOf(steps.values());
    }

    private void validateExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Execution ID must not be null or blank");
        }
    }
}
