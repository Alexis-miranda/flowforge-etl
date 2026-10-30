package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes the steps defined in a pipeline. */
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
            throw new IllegalArgumentException("Step executors must not be null");
        }

        Map<StepType, Map<String, StepExecutor>> executorMap = new HashMap<>();
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
                    executorMap.computeIfAbsent(type, ignored -> new HashMap<>());
            if (connectors.containsKey(connectorKey)) {
                throw new IllegalArgumentException(
                        "Duplicate executor for step type " + type
                                + " and connector: " + connectorKey);
            }
            connectors.put(connectorKey, executor);
        }

        Map<StepType, Map<String, StepExecutor>> immutableMap = new HashMap<>();
        executorMap.forEach(
                (type, connectors) -> immutableMap.put(type, Map.copyOf(connectors)));
        this.executors = Map.copyOf(immutableMap);
        this.listener = listener;
        this.repository = repository;
    }

    /** Executes all steps in their configured order. */
    public PipelineExecutionResult execute(PipelineDefinition pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline must not be null");
        }

        PipelineExecution execution = new PipelineExecution(pipeline.id());
        PipelineExecutionContext context = new PipelineExecutionContext();
        List<StepExecutionRecord> history = new ArrayList<>();

        persistStart(execution);
        notifyStatus(pipeline.id(), execution.status());

        List<PipelineStep> steps = pipeline.steps();
        for (int index = 0; index < steps.size(); index++) {
            PipelineStep step = steps.get(index);
            Instant stepStartedAt = Instant.now();
            StepExecutor stepExecutor;
            try {
                stepExecutor = resolveExecutor(step);
            } catch (IllegalStateException exception) {
                return failExecution(execution, pipeline.id(), step, index,
                        steps, history, exception, stepStartedAt);
            }

            try {
                stepExecutor.execute(step, context);
                history.add(new StepExecutionRecord(
                        step.id(), step.name(), StepExecutionStatus.SUCCEEDED,
                        stepStartedAt, Instant.now(), null, null, null, null, null));
            } catch (RuntimeException exception) {
                return failExecution(execution, pipeline.id(), step, index,
                        steps, history, exception, stepStartedAt);
            }
        }

        execution.completeSuccessfully();
        notifyStatus(pipeline.id(), execution.status());
        return saveResult(toResult(execution, null, null, history));
    }

    private StepExecutor resolveExecutor(PipelineStep step) {
        Map<String, StepExecutor> connectors = executors.get(step.type());
        if (connectors == null || connectors.isEmpty()) {
            throw new IllegalStateException(
                    "No executor registered for step type: " + step.type());
        }

        String connectorKey = step.configuration().get(CONNECTOR_PROPERTY);
        if (connectorKey != null && !connectorKey.isBlank()) {
            StepExecutor executor = connectors.get(connectorKey);
            if (executor == null) {
                throw new IllegalStateException(
                        "No executor registered for step type " + step.type()
                                + " and connector: " + connectorKey);
            }
            return executor;
        }
        if (connectors.size() == 1) {
            return connectors.values().iterator().next();
        }
        throw new IllegalStateException(
                "Multiple executors are registered for step type " + step.type()
                        + ". Specify the '" + CONNECTOR_PROPERTY + "' property.");
    }

    private PipelineExecutionResult failExecution(
            PipelineExecution execution,
            String pipelineId,
            PipelineStep failedStep,
            int failedIndex,
            List<PipelineStep> steps,
            List<StepExecutionRecord> history,
            RuntimeException exception,
            Instant stepStartedAt) {

        String errorMessage = exception.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = exception.getClass().getSimpleName();
        }

        Throwable rootCause = exception;
        Set<Throwable> visitedCauses =
                Collections.newSetFromMap(new IdentityHashMap<>());
        visitedCauses.add(rootCause);
        while (rootCause.getCause() != null
                && visitedCauses.add(rootCause.getCause())) {
            rootCause = rootCause.getCause();
        }

        history.add(new StepExecutionRecord(
                failedStep.id(),
                failedStep.name(),
                StepExecutionStatus.FAILED,
                stepStartedAt,
                Instant.now(),
                exception.getClass().getName(),
                errorMessage,
                rootCause == exception ? null : rootCause.getClass().getName(),
                rootCause == exception ? null : rootCause.getMessage(), null));

        for (int index = failedIndex + 1; index < steps.size(); index++) {
            PipelineStep skipped = steps.get(index);
            history.add(new StepExecutionRecord(
                    skipped.id(), skipped.name(), StepExecutionStatus.SKIPPED,
                    null, Instant.now(), null, null, null, null,
                    "Not executed because step '" + failedStep.id() + "' failed"));
        }

        execution.fail();
        notifyStatus(pipelineId, execution.status());
        return saveResult(toResult(execution, failedStep.id(), errorMessage, history));
    }

    private PipelineExecutionResult toResult(
            PipelineExecution execution,
            String failedStepId,
            String errorMessage,
            List<StepExecutionRecord> history) {
        return new PipelineExecutionResult(
                execution.executionId(),
                execution.pipelineId(),
                execution.status(),
                failedStepId,
                errorMessage,
                execution.startedAt(),
                execution.finishedAt(),
                history);
    }

    private void persistStart(PipelineExecution execution) {
        if (repository == null) {
            return;
        }
        try {
            repository.start(toSnapshot(execution, null, null));
        } catch (RuntimeException exception) {
            throw new PipelineExecutionPersistenceException(
                    execution.executionId(),
                    execution.pipelineId(),
                    List.of(new PipelineExecutionPersistenceException.Failure(
                            PipelineExecutionPersistenceException.Operation.START,
                            exception)),
                    null);
        }
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
     * Persists the final snapshot and result independently. A failure in one
     * operation must not prevent attempting the other operation.
     *
     * @throws PipelineExecutionPersistenceException when any persistence
     *         operation fails; the exception retains the execution result
     */
    private PipelineExecutionResult saveResult(PipelineExecutionResult result) {
        if (repository == null) {
            return result;
        }

        List<PipelineExecutionPersistenceException.Failure> failures =
                new ArrayList<>();
        try {
            repository.update(new PipelineExecutionSnapshot(
                    result.executionId(),
                    result.pipelineId(),
                    result.status(),
                    result.failedStepId(),
                    result.errorMessage(),
                    result.startedAt(),
                    result.finishedAt()));
        } catch (RuntimeException exception) {
            failures.add(new PipelineExecutionPersistenceException.Failure(
                    PipelineExecutionPersistenceException.Operation.UPDATE,
                    exception));
        }

        try {
            repository.save(result);
        } catch (RuntimeException exception) {
            failures.add(new PipelineExecutionPersistenceException.Failure(
                    PipelineExecutionPersistenceException.Operation.SAVE,
                    exception));
        }

        if (!failures.isEmpty()) {
            throw new PipelineExecutionPersistenceException(
                    result.executionId(),
                    result.pipelineId(),
                    failures,
                    result);
        }
        return result;
    }

    /** Notifies the listener without interrupting execution if it fails. */
    private void notifyStatus(String pipelineId, PipelineStatus status) {
        if (listener == null) {
            return;
        }
        try {
            listener.onStatusChanged(pipelineId, status);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING,
                    "Pipeline status listener failed for pipeline " + pipelineId
                            + " while notifying status " + status,
                    exception);
        }
    }
}
