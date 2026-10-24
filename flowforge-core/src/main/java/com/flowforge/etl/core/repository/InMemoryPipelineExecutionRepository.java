
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;

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

    @Override
    public synchronized void start(PipelineExecutionSnapshot snapshot) {
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
            throw new IllegalArgumentException(
                    "Execution snapshot already exists: "
                            + snapshot.executionId());
        }

        snapshots.put(snapshot.executionId(), snapshot);
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

    private void validateExecutionId(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Execution ID must not be null or blank");
        }
    }
}
