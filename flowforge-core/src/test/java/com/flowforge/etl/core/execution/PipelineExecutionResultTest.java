
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionResultTest {

    private static final String EXECUTION_ID = "execution-123";
    private static final String PIPELINE_ID = "customer-import";
    private static final Instant STARTED_AT =
            Instant.parse("2026-10-18T10:00:00Z");
    private static final Instant FINISHED_AT =
            Instant.parse("2026-10-18T10:00:05Z");

    @Test
    void shouldCreateSuccessfulResult() {
        PipelineExecutionResult result = new PipelineExecutionResult(
                EXECUTION_ID,
                PIPELINE_ID,
                PipelineStatus.SUCCEEDED,
                null,
                null,
                STARTED_AT,
                FINISHED_AT
        );

        assertTrue(result.succeeded());
        assertEquals(EXECUTION_ID, result.executionId());
        assertEquals(PIPELINE_ID, result.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(STARTED_AT, result.startedAt());
        assertEquals(FINISHED_AT, result.finishedAt());
    }

    @Test
    void shouldCreateFailedResult() {
        PipelineExecutionResult result = new PipelineExecutionResult(
                EXECUTION_ID,
                PIPELINE_ID,
                PipelineStatus.FAILED,
                "load-customers",
                "Database connection failed",
                STARTED_AT,
                FINISHED_AT
        );

        assertFalse(result.succeeded());
        assertEquals("load-customers", result.failedStepId());
        assertEquals(
                "Database connection failed",
                result.errorMessage()
        );
        assertEquals(EXECUTION_ID, result.executionId());
    }

    @Test
    void shouldRejectBlankExecutionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        " ",
                        PIPELINE_ID,
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        STARTED_AT,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldRejectBlankPipelineId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        " ",
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        STARTED_AT,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldRejectNullTimestamps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        PIPELINE_ID,
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        null,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldRejectFinishTimestampBeforeStartTimestamp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        PIPELINE_ID,
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        FINISHED_AT,
                        STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectRunningResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        PIPELINE_ID,
                        PipelineStatus.RUNNING,
                        null,
                        null,
                        STARTED_AT,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldRejectFailedResultWithoutErrorMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        PIPELINE_ID,
                        PipelineStatus.FAILED,
                        null,
                        null,
                        STARTED_AT,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldRejectFailureDetailsForSuccessfulResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        EXECUTION_ID,
                        PIPELINE_ID,
                        PipelineStatus.SUCCEEDED,
                        null,
                        "Unexpected error",
                        STARTED_AT,
                        FINISHED_AT
                )
        );
    }

    @Test
    void shouldCalculateExecutionDuration() {
        PipelineExecutionResult result = new PipelineExecutionResult(
                EXECUTION_ID,
                PIPELINE_ID,
                PipelineStatus.SUCCEEDED,
                null,
                null,
                STARTED_AT,
                FINISHED_AT
        );

        assertEquals(
                java.time.Duration.ofSeconds(5),
                result.duration()
        );
    }
}
