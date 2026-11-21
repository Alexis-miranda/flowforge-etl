package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.time.Instant;
import java.util.List;
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
        StepExecutionHistory history = new StepExecutionHistory();

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
                history.recordSucceeded(step, stepStartedAt);
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
            StepExecutionHistory history,
            RuntimeException exception,
            Instant stepStartedAt) {

        String errorMessage = history.recordFailed(
                failedStep, exception, stepStartedAt);
        history.recordSkippedAfterFailure(steps, failedIndex, failedStep);

        execution.fail();
        notifyStatus(pipelineId, execution.status());
        return persistence.persistResult(
                toResult(execution, failedStep.id(), errorMessage, history));
    }

    private PipelineExecutionResult toResult(
            PipelineExecution execution,
            String failedStepId,
            String errorMessage,
            StepExecutionHistory history) {
        return new PipelineExecutionResult(
                execution.executionId(),
                execution.pipelineId(),
                execution.status(),
                failedStepId,
                errorMessage,
                execution.startedAt(),
                execution.finishedAt(),
                history.records());
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
