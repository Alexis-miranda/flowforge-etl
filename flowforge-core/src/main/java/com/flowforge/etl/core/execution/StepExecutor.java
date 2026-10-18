
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;

/**
 * Defines the contract for executing a pipeline step.
 */
public interface StepExecutor {

    /**
     * Returns the type of step supported by this executor.
     *
     * @return the supported step type
     */
    StepType supportedType();

    /**
     * Executes the given pipeline step using the shared execution context.
     *
     * @param step the pipeline step to execute
     * @param context the shared context for the pipeline execution
     */
    void execute(PipelineStep step, PipelineExecutionContext context);
}
