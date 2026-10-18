
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single execution of a pipeline.
 */
public class PipelineExecution {

    private final String executionId;
    private final String pipelineId;
    private final Instant startedAt;

    private PipelineStatus status;
    private Instant finishedAt;

    /**
     * Creates a new pipeline execution in the RUNNING state.
     *
     * @param pipelineId the identifier of the pipeline
     * @throws IllegalArgumentException if the pipeline ID is null or blank
     */
    public PipelineExecution(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline ID must not be null or blank");
        }

        this.executionId = UUID.randomUUID().toString();
        this.pipelineId = pipelineId;
        this.status = PipelineStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Marks the execution as successfully completed.
     *
     * @throws IllegalStateException if the execution is already finished
     */
    public void completeSuccessfully() {
        finish(PipelineStatus.SUCCEEDED);
    }

    /**
     * Marks the execution as failed.
     *
     * @throws IllegalStateException if the execution is already finished
     */
    public void fail() {
        finish(PipelineStatus.FAILED);
    }

    private void finish(PipelineStatus finalStatus) {
        if (status != PipelineStatus.RUNNING) {
            throw new IllegalStateException(
                    "Pipeline execution has already finished");
        }

        this.status = finalStatus;
        this.finishedAt = Instant.now();
    }

    public String executionId() {
        return executionId;
    }

    public String pipelineId() {
        return pipelineId;
    }

    public PipelineStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }
}
