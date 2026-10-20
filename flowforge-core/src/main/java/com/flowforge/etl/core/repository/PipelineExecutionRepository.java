
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionResult;

import java.util.List;
import java.util.Optional;

/**
 * Stores pipeline execution results for later retrieval and auditing.
 */
public interface PipelineExecutionRepository {

    /**
     * Stores the result of a completed pipeline execution.
     *
     * @param result the completed execution result to store
     * @throws IllegalArgumentException if the result is null
     */
    void save(PipelineExecutionResult result);

    /**
     * Finds an execution result by its unique identifier.
     *
     * @param executionId the execution identifier
     * @return the result if found, or an empty optional otherwise
     */
    Optional<PipelineExecutionResult> findByExecutionId(
            String executionId);

    /**
     * Finds all execution results associated with a pipeline.
     *
     * @param pipelineId the pipeline identifier
     * @return the matching execution results
     */
    List<PipelineExecutionResult> findByPipelineId(String pipelineId);
}

