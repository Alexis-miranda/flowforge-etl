package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineStepRecoveryEvaluatorTest {

    private final PipelineStepRecoveryEvaluator evaluator =
            new PipelineStepRecoveryEvaluator();
    private final PipelineDefinition pipeline = new PipelineDefinition(
            "cash", "Cash pipeline", "",
            List.of(
                    new PipelineStep("extract", "Extract", StepType.EXTRACT, 1),
                    new PipelineStep("transform", "Transform", StepType.TRANSFORM, 2),
                    new PipelineStep("publish", "Publish", StepType.LOAD, 3)));

    @Test
    void shouldTreatMissingAndSkippedStepsAsNotStarted() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "transform", PersistedStepExecutionStatus.SKIPPED,
                        null, Instant.parse("2026-01-01T00:00:00Z"), null)),
                Map.of());

        assertEquals(List.of(StepRecoveryAction.NOT_STARTED,
                        StepRecoveryAction.NOT_STARTED,
                        StepRecoveryAction.NOT_STARTED),
                result.stream().map(StepRecoveryAssessment::action).toList());
        assertEquals(PersistedStepExecutionStatus.NOT_STARTED, result.get(0).observedStatus());
    }

    @Test
    void shouldRequireReconciliationForRunningStepEvenWhenDeclaredIdempotent() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "extract", PersistedStepExecutionStatus.RUNNING,
                        Instant.parse("2026-01-01T00:00:00Z"), null, null)),
                Map.of("extract", StepRecoveryPolicy.IDEMPOTENT));

        assertEquals(StepRecoveryAction.RECONCILIATION_REQUIRED, result.get(0).action());
    }

    @Test
    void shouldMarkRetryEligibleOnlyWhenPolicyAndFailureClassificationAgree() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "extract", PersistedStepExecutionStatus.FAILED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:01Z"),
                        FailureClassification.RETRYABLE)),
                Map.of("extract", StepRecoveryPolicy.IDEMPOTENT));

        assertEquals(StepRecoveryAction.ELIGIBLE_FOR_RETRY, result.get(0).action());
    }

    @Test
    void shouldRequireReconciliationWhenFailureIsNotClassifiedRetryable() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "extract", PersistedStepExecutionStatus.FAILED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:01Z"),
                        FailureClassification.UNCLASSIFIED)),
                Map.of("extract", StepRecoveryPolicy.IDEMPOTENT));

        assertEquals(StepRecoveryAction.RECONCILIATION_REQUIRED, result.get(0).action());
    }

    @Test
    void shouldRequireManualInterventionForManualOnlyPolicy() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "publish", PersistedStepExecutionStatus.FAILED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:01Z"),
                        FailureClassification.RETRYABLE)),
                Map.of("publish", StepRecoveryPolicy.MANUAL_ONLY));

        assertEquals(StepRecoveryAction.MANUAL_INTERVENTION, result.get(2).action());
    }

    @Test
    void shouldKeepSuccessfulStepCompletedRegardlessOfPolicy() {
        List<StepRecoveryAssessment> result = evaluator.assess(
                pipeline,
                List.of(new PersistedStepExecution(
                        "extract", PersistedStepExecutionStatus.SUCCEEDED,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:01Z"), null)),
                Map.of("extract", StepRecoveryPolicy.MANUAL_ONLY));

        assertEquals(StepRecoveryAction.ALREADY_SUCCEEDED, result.get(0).action());
    }

    @Test
    void shouldRejectDuplicatePersistedStepRecords() {
        PersistedStepExecution record = new PersistedStepExecution(
                "extract", PersistedStepExecutionStatus.RUNNING,
                Instant.parse("2026-01-01T00:00:00Z"), null, null);

        assertThrows(IllegalArgumentException.class, () -> evaluator.assess(
                pipeline, List.of(record, record), Map.of()));
    }

    @Test
    void shouldRejectPersistedStepThatDoesNotBelongToPipeline() {
        PersistedStepExecution record = new PersistedStepExecution(
                "unknown", PersistedStepExecutionStatus.RUNNING,
                Instant.parse("2026-01-01T00:00:00Z"), null, null);

        assertThrows(IllegalArgumentException.class, () -> evaluator.assess(
                pipeline, List.of(record), Map.of()));
    }
}
