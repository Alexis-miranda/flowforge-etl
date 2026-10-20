
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

/**
 * Stores pipeline execution results in memory.
 *
 * <p>Results are kept in insertion order and are lost when the
 * application stops.</p>
 */
public class InMemoryPipelineExecutionRepository
        implements PipelineExecutionRepository {

    private final Map<String, PipelineExecutionResult> executions =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /**
     * Stores a completed pipeline execution result.
     *
     * @param result the execution result to store
     * @throws IllegalArgumentException if the result is null or its
     *                                  execution ID already exists
     */
    @Override
    public void save(PipelineExecutionResult result) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "Execution result must not be null");
        }

        synchronized (executions) {
            if (executions.containsKey(result.executionId())) {
                throw new IllegalArgumentException(
                        "Execution result already exists: "
                                + result.executionId());
            }

            executions.put(result.executionId(), result);
        }
    }

    /**
     * Finds an execution result by its unique identifier.
     *
     * @param executionId the execution identifier
     * @return the result if found, or an empty optional otherwise
     */
    @Override
    public Optional<PipelineExecutionResult> findByExecutionId(
            String executionId) {
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Execution ID must not be null or blank");
        }
        return Optional.ofNullable(executions.get(executionId));
    }

    /**
     * Finds all execution results associated with a pipeline.
     *
     * @param pipelineId the pipeline identifier
     * @return an immutable list of matching results in insertion order
     */
    @Override
    public List<PipelineExecutionResult> findByPipelineId(
            String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be null or blank");
        }

        synchronized (executions) {
            List<PipelineExecutionResult> results = new ArrayList<>();

            for (PipelineExecutionResult result : executions.values()) {
                if (result.pipelineId().equals(pipelineId)) {
                    results.add(result);
                }
            }

            return List.copyOf(results);
        }
    }
}
