
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionResultTest {

    @Test
    void shouldCreateSuccessfulResult() {
        PipelineExecutionResult result = new PipelineExecutionResult(
                "customer-import",
                PipelineStatus.SUCCEEDED,
                null,
                null
        );

        assertTrue(result.succeeded());
        assertEquals("customer-import", result.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
    }

    @Test
    void shouldCreateFailedResult() {
        PipelineExecutionResult result = new PipelineExecutionResult(
                "customer-import",
                PipelineStatus.FAILED,
                "load-customers",
                "Database connection failed"
        );

        assertFalse(result.succeeded());
        assertEquals("load-customers", result.failedStepId());
        assertEquals("Database connection failed", result.errorMessage());
    }

    @Test
    void shouldRejectBlankPipelineId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        " ",
                        PipelineStatus.SUCCEEDED,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectFailedResultWithoutErrorMessage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        "customer-import",
                        PipelineStatus.FAILED,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectFailureDetailsForSuccessfulResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutionResult(
                        "customer-import",
                        PipelineStatus.SUCCEEDED,
                        null,
                        "Unexpected error"
                )
        );
    }
}
