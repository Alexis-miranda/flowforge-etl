
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

    private static final String CONNECTOR_PROPERTY = "connector";

    private final Map<StepType, Map<String, StepExecutor>> executors;
    private final PipelineExecutionListener listener;
    private final PipelineExecutionRepository repository;

    public PipelineExecutor(List<StepExecutor> stepExecutors) {
        this(stepExecutors, null, null);
    }

    public PipelineExecutor(
            List<StepExecutor> stepExecutors,
            PipelineExecutionListener listener) {
        this(stepExecutors, listener, null);
    }

    public PipelineExecutor(
            List<StepExecutor> stepExecutors,
            PipelineExecutionListener listener,
            PipelineExecutionRepository repository) {

        if (stepExecutors == null) {
            throw new IllegalArgumentException(
                    "Step executors must not be null");
        }

        Map<StepType, Map<String, StepExecutor>> executorMap =
                new HashMap<>();

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

            String connectorKey = executor.connectorKey();

            if (connectorKey == null || connectorKey.isBlank()) {
                throw new IllegalArgumentException(
                        "Executor connector key must not be blank");
            }

            Map<String, StepExecutor> connectors =
                    executorMap.computeIfAbsent(
                            type, ignored -> new HashMap<>());

            if (connectors.containsKey(connectorKey)) {
                throw new IllegalArgumentException(
                        "Duplicate executor for step type "
                                + type
                                + " and connector: "
                                + connectorKey);
            }

            connectors.put(connectorKey, executor);
        }

        Map<StepType, Map<String, StepExecutor>> immutableMap =
                new HashMap<>();

        executorMap.forEach(
                (type, connectors) ->
                        immutableMap.put(type, Map.copyOf(connectors)));

        this.executors = Map.copyOf(immutableMap);
        this.listener = listener;
        this.repository = repository;
    }

    /**
     * Executes all steps in the pipeline in their defined order.
     *
     * @param pipeline the pipeline definition
     * @return the detailed execution result
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

        persistStart(execution);
        notifyStatus(pipeline.id(), execution.status());

        for (PipelineStep step : pipeline.steps()) {
            Map<String, StepExecutor> connectors =
                    executors.get(step.type());

            if (connectors == null || connectors.isEmpty()) {
                return failExecution(
                        execution,
                        pipeline.id(),
                        step,
                        "No executor registered for step type: "
                                + step.type());
            }

            String connectorKey =
                    step.configuration().get(CONNECTOR_PROPERTY);

            StepExecutor executor;

            if (connectorKey != null && !connectorKey.isBlank()) {
                executor = connectors.get(connectorKey);

                if (executor == null) {
                    return failExecution(
                            execution,
                            pipeline.id(),
                            step,
                            "No executor registered for step type "
                                    + step.type()
                                    + " and connector: "
                                    + connectorKey);
                }
            } else if (connectors.size() == 1) {
                executor = connectors.values().iterator().next();
            } else {
                return failExecution(
                        execution,
                        pipeline.id(),
                        step,
                        "Multiple executors are registered for step type "
                                + step.type()
                                + ". Specify the '"
                                + CONNECTOR_PROPERTY
                                + "' property.");
            }

            try {
                executor.execute(step, context);
            } catch (RuntimeException exception) {
                String errorMessage = exception.getMessage();

                if (errorMessage == null || errorMessage.isBlank()) {
                    errorMessage = exception.getClass().getSimpleName();
                }

                return failExecution(
                        execution,
                        pipeline.id(),
                        step,
                        errorMessage);
            }
        }

        execution.completeSuccessfully();
        notifyStatus(pipeline.id(), execution.status());

        return saveResult(execution.toResult(null, null));
    }

    private PipelineExecutionResult failExecution(
            PipelineExecution execution,
            String pipelineId,
            PipelineStep step,
            String errorMessage) {

        execution.fail();
        notifyStatus(pipelineId, execution.status());

        return saveResult(
                execution.toResult(step.id(), errorMessage));
    }

    private void persistStart(PipelineExecution execution) {
        if (repository == null) {
            return;
        }

        repository.start(toSnapshot(execution, null, null));
    }

    private PipelineExecutionSnapshot toSnapshot(
            PipelineExecution execution,
            String failedStepId,
            String errorMessage) {

        return new PipelineExecutionSnapshot(
                execution.executionId(),
                execution.pipelineId(),
                execution.status(),
                failedStepId,
                errorMessage,
                execution.startedAt(),
                execution.finishedAt());
    }

    /**
     * Saves the final snapshot and result when a repository is configured.
     */
    private PipelineExecutionResult saveResult(
            PipelineExecutionResult result) {

        if (repository != null) {
            repository.update(
                    new PipelineExecutionSnapshot(
                            result.executionId(),
                            result.pipelineId(),
                            result.status(),
                            result.failedStepId(),
                            result.errorMessage(),
                            result.startedAt(),
                            result.finishedAt()));

            repository.save(result);
        }

        return result;
    }

    /**
     * Notifies the listener without interrupting execution if it fails.
     */
    private void notifyStatus(
            String pipelineId,
            PipelineStatus status) {

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
                    exception);
        }
    }
}
