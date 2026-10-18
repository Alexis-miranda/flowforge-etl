
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;

/**
 * Receives notifications when a pipeline execution changes its status.
 */
public interface PipelineExecutionListener {

    /**
     * Called when a pipeline execution changes its status.
     *
     * @param pipelineId the identifier of the pipeline
     * @param status the new execution status
     */
    void onStatusChanged(String pipelineId, PipelineStatus status);
}
