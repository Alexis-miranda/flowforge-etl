package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.time.Instant;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence of execution snapshots and completed execution results.
 */
final class PipelineExecutionPersistence {

    private final PipelineExecutionRepository repository;

    PipelineExecutionPersistence(PipelineExecutionRepository repository) {
        this.repository = repository;
    }

    void persistStart(PipelineExecution execution) {
        if (repository == null) {
            return;
        }

        try {
            repository.start(toSnapshot(
                    execution.executionId(),
                    execution.pipelineId(),
                    execution.status(),
                    null,
                    null,
                    execution.startedAt(),
                    execution.finishedAt()));
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

    PipelineExecutionResult persistResult(PipelineExecutionResult result) {
        if (repository == null) {
            return result;
        }

        List<PipelineExecutionPersistenceException.Failure> failures =
                new ArrayList<>();
        try {
            repository.update(toSnapshot(
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

    private PipelineExecutionSnapshot toSnapshot(
            String executionId,
            String pipelineId,
            PipelineStatus status,
            String failedStepId,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {
        return new PipelineExecutionSnapshot(
                executionId,
                pipelineId,
                status,
                failedStepId,
                errorMessage,
                startedAt,
                finishedAt);
    }
}
