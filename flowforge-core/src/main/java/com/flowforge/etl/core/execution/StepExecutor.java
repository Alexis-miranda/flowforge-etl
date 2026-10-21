
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
     * Returns the connector key supported by this executor.
     *
     * Existing executors use the default key until they declare
     * a specific connector.
     *
     * @return the connector key
     */
    default String connectorKey() {
        return "default";
    }

    /**
     * Executes the given pipeline step using the shared execution context.
     *
     * @param step the pipeline step to execute
     * @param context the shared context for the pipeline execution
     */
    void execute(PipelineStep step, PipelineExecutionContext context);
}
