package com.flowforge.etl.core.execution;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepExecutionRecordTest {

    private static final Instant STARTED_AT =
            Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant FINISHED_AT =
            Instant.parse("2026-10-30T10:00:01Z");

    @Test
    void shouldDefaultLegacyFailedRecordToUnclassified() {
        StepExecutionRecord record = new StepExecutionRecord(
                "extract",
                "Extract",
                StepExecutionStatus.FAILED,
                STARTED_AT,
                FINISHED_AT,
                IllegalStateException.class.getName(),
                "Extraction failed",
                null,
                null,
                null);

        assertEquals(
                FailureClassification.UNCLASSIFIED,
                record.failureClassification());
    }

    @Test
    void shouldRejectClassificationForSuccessfulStep() {
        assertThrows(IllegalArgumentException.class, () ->
                new StepExecutionRecord(
                        "extract",
                        "Extract",
                        StepExecutionStatus.SUCCEEDED,
                        STARTED_AT,
                        FINISHED_AT,
                        null,
                        null,
                        null,
                        null,
                        null,
                        FailureClassification.RETRYABLE));
    }

    @Test
    void shouldRejectFailedStepWithoutClassificationInCanonicalConstructor() {
        assertThrows(IllegalArgumentException.class, () ->
                new StepExecutionRecord(
                        "extract",
                        "Extract",
                        StepExecutionStatus.FAILED,
                        STARTED_AT,
                        FINISHED_AT,
                        IllegalStateException.class.getName(),
                        "Extraction failed",
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void shouldNotAssignClassificationToSkippedStep() {
        StepExecutionRecord record = new StepExecutionRecord(
                "load",
                "Load",
                StepExecutionStatus.SKIPPED,
                null,
                FINISHED_AT,
                null,
                null,
                null,
                null,
                "Previous step failed");

        assertNull(record.failureClassification());
    }
}
