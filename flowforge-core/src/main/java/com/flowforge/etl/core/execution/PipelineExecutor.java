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
    private static final Logger LOGGER = Logger.getLogger(PipelineExecutor.class.getName());
    private final StepExecutorRegistry executorRegistry;
    private final PipelineExecutionListener listener;
    private final PipelineExecutionPersistence persistence;

    public PipelineExecutor(List<StepExecutor> stepExecutors) { this(stepExecutors, null, null); }
    public PipelineExecutor(List<StepExecutor> stepExecutors, PipelineExecutionListener listener) {
        this(stepExecutors, listener, null);
    }
    public PipelineExecutor(List<StepExecutor> stepExecutors, PipelineExecutionListener listener,
                            PipelineExecutionRepository repository) {
        this.executorRegistry = new StepExecutorRegistry(stepExecutors);
        this.listener = listener;
        this.persistence = new PipelineExecutionPersistence(repository);
    }

    /** Executes all steps using a generated execution ID. */
    public PipelineExecutionResult execute(PipelineDefinition pipeline) {
        validatePipeline(pipeline);
        return execute(pipeline, new PipelineExecution(pipeline.id()));
    }

    /** Executes all steps using the supplied execution ID. */
    public PipelineExecutionResult execute(PipelineDefinition pipeline, String executionId) {
        validatePipeline(pipeline);
        return execute(pipeline, new PipelineExecution(pipeline.id(), executionId));
    }

    /**
     * Executes a pipeline under the supplied lease. This method does not
     * acquire, renew, or release the lease.
     */
    public PipelineExecutionResult executeWithLease(PipelineDefinition pipeline,
                                                     PipelineExecutionLease lease) {
        validatePipeline(pipeline);
        if (lease == null) throw new IllegalArgumentException("Execution lease must not be null");
        PipelineExecution execution = new PipelineExecution(pipeline.id(), lease.executionId());
        return execute(pipeline, execution, lease);
    }

    private PipelineExecutionResult execute(PipelineDefinition pipeline, PipelineExecution execution) {
        return execute(pipeline, execution, null);
    }

    private PipelineExecutionResult execute(PipelineDefinition pipeline, PipelineExecution execution,
                                            PipelineExecutionLease lease) {
        PipelineExecutionContext context =
                new PipelineExecutionContext(execution.executionId(), lease);
        StepExecutionHistory history = new StepExecutionHistory();
        persistence.persistStart(execution);
        notifyStatus(pipeline.id(), execution.status());

        List<PipelineStep> steps = pipeline.steps();
        for (int index = 0; index < steps.size(); index++) {
            PipelineStep step = steps.get(index);
            Instant stepStartedAt = Instant.now();
            persistence.persistStepStart(execution.executionId(), step, stepStartedAt);
            StepExecutor stepExecutor;
            try {
                stepExecutor = executorRegistry.resolve(step);
            } catch (IllegalStateException exception) {
                return failExecution(execution, pipeline.id(), step, index,
                        steps, history, exception, stepStartedAt);
            }
            try {
                stepExecutor.execute(step, context);
            } catch (RuntimeException exception) {
                return failExecution(execution, pipeline.id(), step, index,
                        steps, history, exception, stepStartedAt);
            }
            history.recordSucceeded(step, stepStartedAt);
            persistence.persistStepFinish(execution.executionId(),
                    history.records().get(history.records().size() - 1));
        }
        execution.completeSuccessfully();
        notifyStatus(pipeline.id(), execution.status());
        return persistence.persistResult(toResult(execution, null, null, history));
    }

    private void validatePipeline(PipelineDefinition pipeline) {
        if (pipeline == null) throw new IllegalArgumentException("Pipeline must not be null");
    }

    private PipelineExecutionResult failExecution(PipelineExecution execution, String pipelineId,
            PipelineStep failedStep, int failedIndex, List<PipelineStep> steps,
            StepExecutionHistory history, RuntimeException exception, Instant stepStartedAt) {
        String errorMessage = history.recordFailed(failedStep, exception, stepStartedAt);
        history.recordSkippedAfterFailure(steps, failedIndex, failedStep);
        List<StepExecutionRecord> records = history.records();
        for (int index = failedIndex; index < records.size(); index++) {
            persistence.persistStepFinish(execution.executionId(), records.get(index));
        }
        execution.fail();
        notifyStatus(pipelineId, execution.status());
        return persistence.persistResult(toResult(execution, failedStep.id(), errorMessage, history));
    }

    private PipelineExecutionResult toResult(PipelineExecution execution, String failedStepId,
            String errorMessage, StepExecutionHistory history) {
        return new PipelineExecutionResult(execution.executionId(), execution.pipelineId(),
                execution.status(), failedStepId, errorMessage, execution.startedAt(),
                execution.finishedAt(), history.records());
    }

    /** Notifies the listener without interrupting execution if it fails. */
    private void notifyStatus(String pipelineId, PipelineStatus status) {
        if (listener == null) return;
        try {
            listener.onStatusChanged(pipelineId, status);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Pipeline status listener failed for pipeline " + pipelineId
                    + " while notifying status " + status, exception);
        }
    }
}