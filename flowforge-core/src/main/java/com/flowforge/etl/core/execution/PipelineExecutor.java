package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes the steps defined in a pipeline. */
public class PipelineExecutor {

    private static final Logger LOGGER =
            Logger.getLogger(PipelineExecutor.class.getName());

    private final StepExecutorRegistry executorRegistry;
    private final PipelineExecutionListener listener;
    private final PipelineExecutionPersistence persistence;

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
        this.executorRegistry = new StepExecutorRegistry(stepExecutors);
        this.listener = listener;
        this.persistence = new PipelineExecutionPersistence(repository);
    }

    /** Executes all steps in their configured order. */
    public PipelineExecutionResult execute(PipelineDefinition pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline must not be null");
        }

        PipelineExecution execution = new PipelineExecution(pipeline.id());
        PipelineExecutionContext context = new PipelineExecutionContext();
        List<StepExecutionRecord> history = new ArrayList<>();

        persistence.persistStart(execution);
        notifyStatus(pipeline.id(), execution.status());

        List<PipelineStep> steps = pipeline.steps();
        for (int index = 0; index < steps.size(); index++) {
            PipelineStep step = steps.get(index);
            Instant stepStartedAt = Instant.now();
            StepExecutor stepExecutor;
            try {
                stepExecutor = executorRegistry.resolve(step);
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
        return persistence.persistResult(toResult(execution, null, null, history));
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

        FailureClassification failureClassification =
                exception instanceof StepExecutionFailureException classifiedFailure
                        ? classifiedFailure.failureClassification()
                        : FailureClassification.UNCLASSIFIED;

        history.add(new StepExecutionRecord(
                failedStep.id(),
                failedStep.name(),
                StepExecutionStatus.FAILED,
                stepStartedAt,
                Instant.now(),
                exception.getClass().getName(),
                errorMessage,
                rootCause == exception ? null : rootCause.getClass().getName(),
                rootCause == exception ? null : rootCause.getMessage(),
                null,
                failureClassification));

        for (int index = failedIndex + 1; index < steps.size(); index++) {
            PipelineStep skipped = steps.get(index);
            history.add(new StepExecutionRecord(
                    skipped.id(), skipped.name(), StepExecutionStatus.SKIPPED,
                    null, Instant.now(), null, null, null, null,
                    "Not executed because step '" + failedStep.id() + "' failed"));
        }

        execution.fail();
        notifyStatus(pipelineId, execution.status());
        return persistence.persistResult(
                toResult(execution, failedStep.id(), errorMessage, history));
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
