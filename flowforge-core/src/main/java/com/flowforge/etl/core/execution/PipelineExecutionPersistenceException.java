package com.flowforge.etl.core.execution;

import java.util.List;
import java.util.Optional;

/**
 * Reports failures while recording a pipeline execution in its repository.
 *
 * <p>The pipeline outcome is kept separate from audit persistence. When the
 * pipeline has completed, {@link #executionResult()} exposes its original
 * result even if saving the audit information failed.</p>
 */
public final class PipelineExecutionPersistenceException extends RuntimeException {

    private final String executionId;
    private final String pipelineId;
    private final List<Failure> failures;
    private final PipelineExecutionResult executionResult;

    public PipelineExecutionPersistenceException(
            String executionId,
            String pipelineId,
            List<Failure> failures,
            PipelineExecutionResult executionResult) {
        super(buildMessage(executionId, failures), firstCause(failures));
        if (executionId == null || executionId.isBlank()) {
            throw new IllegalArgumentException("Execution ID must not be blank");
        }
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("Pipeline ID must not be blank");
        }
        if (failures == null || failures.isEmpty()) {
            throw new IllegalArgumentException("Persistence failures must not be empty");
        }
        this.executionId = executionId;
        this.pipelineId = pipelineId;
        this.failures = List.copyOf(failures);
        this.executionResult = executionResult;

        for (int index = 1; index < this.failures.size(); index++) {
            Throwable additionalCause = this.failures.get(index).cause();
            if (additionalCause != getCause()) {
                addSuppressed(additionalCause);
            }
        }
    }

    public String executionId() {
        return executionId;
    }

    public String pipelineId() {
        return pipelineId;
    }

    public List<Failure> failures() {
        return failures;
    }

    /**
     * Returns the completed pipeline result, or empty when the initial
     * execution registration failed before pipeline steps could run.
     */
    public Optional<PipelineExecutionResult> executionResult() {
        return Optional.ofNullable(executionResult);
    }

    private static Throwable firstCause(List<Failure> failures) {
        if (failures == null || failures.isEmpty() || failures.get(0) == null) {
            throw new IllegalArgumentException(
                    "At least one valid persistence failure is required");
        }
        return failures.get(0).cause();
    }

    private static String buildMessage(
            String executionId,
            List<Failure> failures) {
        if (failures == null || failures.isEmpty() || failures.get(0) == null) {
            return "Unable to persist pipeline execution " + executionId;
        }
        return "Unable to persist pipeline execution " + executionId
                + "; failed operation(s): "
                + failures.stream()
                .map(failure -> failure.operation().name())
                .distinct()
                .toList();
    }

    public enum Operation {
        START,
        UPDATE,
        SAVE
    }

    /** Describes one failed repository operation. */
    public record Failure(Operation operation, Throwable cause) {
        public Failure {
            if (operation == null) {
                throw new IllegalArgumentException(
                        "Persistence operation must not be null");
            }
            if (cause == null) {
                throw new IllegalArgumentException(
                        "Persistence failure cause must not be null");
            }
        }
    }
}
