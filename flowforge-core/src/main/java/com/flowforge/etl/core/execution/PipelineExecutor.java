
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the steps defined in a pipeline.
 */
public class PipelineExecutor {

    private final Map<StepType, StepExecutor> executors;

    /**
     * Creates a pipeline executor using the provided step executors.
     *
     * @param stepExecutors the available step executors
     * @throws IllegalArgumentException if the list is null, contains null
     *                                  executors, or has duplicate step types
     */
    public PipelineExecutor(List<StepExecutor> stepExecutors) {
        if (stepExecutors == null) {
            throw new IllegalArgumentException(
                    "Step executors must not be null");
        }

        Map<StepType, StepExecutor> executorMap = new HashMap<>();

        for (StepExecutor executor : stepExecutors) {
            if (executor == null) {
                throw new IllegalArgumentException(
                        "Step executors must not contain null elements");
            }

            StepType type = executor.supportedType();

            if (type == null) {
                throw new IllegalArgumentException(
                        "Executor supported type must not be null");
            }

            if (executorMap.containsKey(type)) {
                throw new IllegalArgumentException(
                        "Duplicate executor for step type: " + type);
            }

            executorMap.put(type, executor);
        }

        this.executors = Map.copyOf(executorMap);
    }

    /**
     * Executes all steps in the pipeline in their defined order.
     *
     * @param pipeline the pipeline definition
     * @return the detailed execution result
     * @throws IllegalArgumentException if the pipeline is null
     */
    public PipelineExecutionResult execute(PipelineDefinition pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException(
                    "Pipeline must not be null");
        }

        for (PipelineStep step : pipeline.steps()) {
            StepExecutor executor = executors.get(step.type());

            if (executor == null) {
                return new PipelineExecutionResult(
                        pipeline.id(),
                        PipelineStatus.FAILED,
                        step.id(),
                        "No executor registered for step type: "
                                + step.type()
                );
            }

            try {
                executor.execute(step);
            } catch (RuntimeException exception) {
                String errorMessage = exception.getMessage();

                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = exception.getClass().getSimpleName();
                }

                return new PipelineExecutionResult(
                        pipeline.id(),
                        PipelineStatus.FAILED,
                        step.id(),
                        errorMessage
                );
            }
        }

        return new PipelineExecutionResult(
                pipeline.id(),
                PipelineStatus.SUCCEEDED,
                null,
                null
        );
    }
}
