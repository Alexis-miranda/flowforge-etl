
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executes the steps defined in a pipeline.
 */
public class PipelineExecutor {

    private final Map<StepType, StepExecutor> executors;

    /**
     * Creates a pipeline executor using the provided step executors.
     *
     * @param stepExecutors the available step executors
     */
    public PipelineExecutor(List<StepExecutor> stepExecutors) {
        if (stepExecutors == null) {
            throw new IllegalArgumentException(
                    "Step executors must not be null");
        }

        this.executors = stepExecutors.stream()
                .collect(Collectors.toMap(
                        StepExecutor::supportedType,
                        Function.identity()
                ));
    }

    /**
     * Executes all steps in the pipeline in their defined order.
     *
     * @param pipeline the pipeline definition
     * @return the final pipeline status
     */
    public PipelineStatus execute(PipelineDefinition pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException(
                    "Pipeline must not be null");
        }

        try {
            for (PipelineStep step : pipeline.steps()) {
                StepExecutor executor = executors.get(step.type());

                if (executor == null) {
                    return PipelineStatus.FAILED;
                }

                executor.execute(step);
            }

            return PipelineStatus.SUCCEEDED;
        } catch (RuntimeException exception) {
            return PipelineStatus.FAILED;
        }
    }
}
