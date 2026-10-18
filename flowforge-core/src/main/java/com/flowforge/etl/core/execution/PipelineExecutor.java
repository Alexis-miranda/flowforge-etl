
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
    private final PipelineExecutionListener listener;

    /**
     * Creates a pipeline executor without a status listener.
     *
     * @param stepExecutors the available step executors
     */
    public PipelineExecutor(List<StepExecutor> stepExecutors) {
        this(stepExecutors, null);
    }

    /**
     * Creates a pipeline executor with a status listener.
     *
     * @param stepExecutors the available step executors
     * @param listener the listener for execution status changes
     */
    public PipelineExecutor(
            List<StepExecutor> stepExecutors,
            PipelineExecutionListener listener) {

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
        this.listener = listener;
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

        notifyStatus(pipeline.id(), PipelineStatus.RUNNING);

        for (PipelineStep step : pipeline.steps()) {
            StepExecutor executor = executors.get(step.type());

            if (executor == null) {
                return failedResult(
                        pipeline.id(),
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

                return failedResult(
                        pipeline.id(),
                        step.id(),
                        errorMessage
                );
            }
        }

        notifyStatus(pipeline.id(), PipelineStatus.SUCCEEDED);

        return new PipelineExecutionResult(
                pipeline.id(),
                PipelineStatus.SUCCEEDED,
                null,
                null
        );
    }

    private PipelineExecutionResult failedResult(
            String pipelineId,
            String failedStepId,
            String errorMessage) {

        notifyStatus(pipelineId, PipelineStatus.FAILED);

        return new PipelineExecutionResult(
                pipelineId,
                PipelineStatus.FAILED,
                failedStepId,
                errorMessage
        );
    }

    private void notifyStatus(String pipelineId, PipelineStatus status) {
        if (listener != null) {
            listener.onStatusChanged(pipelineId, status);
        }
    }
}

