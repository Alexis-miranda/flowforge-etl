
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes the steps defined in a pipeline.
 */
public class PipelineExecutor {

    private static final Logger LOGGER =
            Logger.getLogger(PipelineExecutor.class.getName());

    private final Map<StepType, StepExecutor> executors;
    private final PipelineExecutionListener listener;
    private final PipelineExecutionRepository repository;

    /**
     * Creates a pipeline executor without a status listener or repository.
     *
     * @param stepExecutors the available step executors
     */
    public PipelineExecutor(List<StepExecutor> stepExecutors) {
        this(stepExecutors, null, null);
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
        this(stepExecutors, listener, null);
    }

    /**
     * Creates a pipeline executor with a status listener and repository.
     *
     * @param stepExecutors the available step executors
     * @param listener the listener for execution status changes
     * @param repository the repository for completed execution results
     */
    public PipelineExecutor(
            List<StepExecutor> stepExecutors,
            PipelineExecutionListener listener,
            PipelineExecutionRepository repository) {

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
        this.repository = repository;
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

        PipelineExecution execution =
                new PipelineExecution(pipeline.id());

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        notifyStatus(pipeline.id(), execution.status());

        for (PipelineStep step : pipeline.steps()) {
            StepExecutor executor = executors.get(step.type());

            if (executor == null) {
                execution.fail();
                notifyStatus(pipeline.id(), execution.status());

                return saveResult(
                        execution.toResult(
                                step.id(),
                                "No executor registered for step type: "
                                        + step.type()
                        )
                );
            }

            try {
                executor.execute(step, context);
            } catch (RuntimeException exception) {
                String errorMessage = exception.getMessage();

                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = exception.getClass().getSimpleName();
                }

                execution.fail();
                notifyStatus(pipeline.id(), execution.status());

                return saveResult(
                        execution.toResult(step.id(), errorMessage)
                );
            }
        }

        execution.completeSuccessfully();
        notifyStatus(pipeline.id(), execution.status());

        return saveResult(execution.toResult(null, null));
    }

    /**
     * Saves a completed execution result when a repository is configured.
     *
     * @param result the completed execution result
     * @return the same execution result
     */
    private PipelineExecutionResult saveResult(
            PipelineExecutionResult result) {

        if (repository != null) {
            repository.save(result);
        }

        return result;
    }

    /**
     * Notifies the listener without interrupting pipeline execution
     * if the listener fails.
     *
     * @param pipelineId the identifier of the pipeline
     * @param status the new execution status
     */
    private void notifyStatus(String pipelineId, PipelineStatus status) {
        if (listener == null) {
            return;
        }

        try {
            listener.onStatusChanged(pipelineId, status);
        } catch (RuntimeException exception) {
            LOGGER.log(
                    Level.WARNING,
                    "Pipeline status listener failed for pipeline "
                            + pipelineId
                            + " while notifying status "
                            + status,
                    exception
            );
        }
    }
}
