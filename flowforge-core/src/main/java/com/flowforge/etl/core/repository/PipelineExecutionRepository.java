
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Stores pipeline execution state and results for auditing.
 */
public interface PipelineExecutionRepository {

    /**
     * Registers a new execution snapshot.
     *
     * @param snapshot the initial execution snapshot
     */
    void start(PipelineExecutionSnapshot snapshot);

    /**
     * Updates an existing execution snapshot.
     *
     * @param snapshot the latest execution snapshot
     */
    void update(PipelineExecutionSnapshot snapshot);

    /**
     * Stores the result of a completed pipeline execution.
     *
     * @param result the completed execution result to store
     */
    void save(PipelineExecutionResult result);

    /**
     * Finds the latest execution snapshot by execution ID.
     *
     * @param executionId the execution identifier
     * @return the snapshot if found
     */
    Optional<PipelineExecutionSnapshot> findSnapshotByExecutionId(
            String executionId);

    /**
     * Finds all execution results associated with a pipeline.
     *
     * @param pipelineId the pipeline identifier
     * @return matching completed execution results
     */
    List<PipelineExecutionResult> findByPipelineId(String pipelineId);

    /**
     * Finds a completed execution result by its unique identifier.
     *
     * @param executionId the execution identifier
     * @return the result if found
     */
    Optional<PipelineExecutionResult> findByExecutionId(
            String executionId);

    /**
     * Finds all execution snapshots associated with a pipeline,
     * including executions that are still running.
     *
     * @param pipelineId the pipeline identifier
     * @return matching execution snapshots, newest first
     */
    List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(
            String pipelineId);
}
