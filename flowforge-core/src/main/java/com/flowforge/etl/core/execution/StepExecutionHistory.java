package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Builds the ordered diagnostic history of a sequential step execution. */
final class StepExecutionHistory {

    private final List<StepExecutionRecord> records = new ArrayList<>();

    void recordSucceeded(PipelineStep step, Instant startedAt) {
        records.add(new StepExecutionRecord(
                step.id(), step.name(), StepExecutionStatus.SUCCEEDED,
                startedAt, Instant.now(), null, null, null, null, null));
    }

    String recordFailed(
            PipelineStep step,
            RuntimeException exception,
            Instant startedAt) {
        String errorMessage = exception.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = exception.getClass().getSimpleName();
        }

        Throwable rootCause = findRootCause(exception);
        FailureClassification failureClassification =
                exception instanceof StepExecutionFailureException classifiedFailure
                        ? classifiedFailure.failureClassification()
                        : FailureClassification.UNCLASSIFIED;

        records.add(new StepExecutionRecord(
                step.id(),
                step.name(),
                StepExecutionStatus.FAILED,
                startedAt,
                Instant.now(),
                exception.getClass().getName(),
                errorMessage,
                rootCause == exception ? null : rootCause.getClass().getName(),
                rootCause == exception ? null : rootCause.getMessage(),
                null,
                failureClassification));
        return errorMessage;
    }

    void recordSkippedAfterFailure(
            List<PipelineStep> steps,
            int failedIndex,
            PipelineStep failedStep) {
        String skipReason =
                "Not executed because step '" + failedStep.id() + "' failed";
        for (int index = failedIndex + 1; index < steps.size(); index++) {
            PipelineStep skipped = steps.get(index);
            records.add(new StepExecutionRecord(
                    skipped.id(), skipped.name(), StepExecutionStatus.SKIPPED,
                    null, Instant.now(), null, null, null, null, skipReason));
        }
    }

    List<StepExecutionRecord> records() {
        return List.copyOf(records);
    }

    private Throwable findRootCause(Throwable exception) {
        Throwable rootCause = exception;
        Set<Throwable> visitedCauses =
                Collections.newSetFromMap(new IdentityHashMap<>());
        visitedCauses.add(rootCause);
        while (rootCause.getCause() != null
                && visitedCauses.add(rootCause.getCause())) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}
