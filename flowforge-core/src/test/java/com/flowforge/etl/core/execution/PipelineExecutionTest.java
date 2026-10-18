
package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionTest {

    @Test
    void shouldStartExecutionInRunningState() {
        PipelineExecution execution =
                new PipelineExecution("pipeline-1");

        assertEquals("pipeline-1", execution.pipelineId());
        assertEquals(PipelineStatus.RUNNING, execution.status());
        assertNotNull(execution.executionId());
        assertNotNull(execution.startedAt());
        assertNull(execution.finishedAt());
    }

    @Test
    void shouldGenerateUniqueExecutionIds() {
        PipelineExecution first =
                new PipelineExecution("pipeline-1");

        PipelineExecution second =
                new PipelineExecution("pipeline-1");

        assertNotEquals(first.executionId(), second.executionId());
    }

    @Test
    void shouldCompleteExecutionSuccessfully() {
        PipelineExecution execution =
                new PipelineExecution("pipeline-1");

        execution.completeSuccessfully();

        assertEquals(PipelineStatus.SUCCEEDED, execution.status());
        assertNotNull(execution.finishedAt());
        assertTrue(
                !execution.finishedAt().isBefore(execution.startedAt())
        );
    }

    @Test
    void shouldFailExecution() {
        PipelineExecution execution =
                new PipelineExecution("pipeline-1");

        execution.fail();

        assertEquals(PipelineStatus.FAILED, execution.status());
        assertNotNull(execution.finishedAt());
    }

    @Test
    void shouldRejectNullOrBlankPipelineId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecution(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecution(" ")
        );
    }

    @Test
    void shouldRejectCompletingExecutionMoreThanOnce() {
        PipelineExecution execution =
                new PipelineExecution("pipeline-1");

        execution.completeSuccessfully();

        assertThrows(
                IllegalStateException.class,
                execution::completeSuccessfully
        );
    }

    @Test
    void shouldRejectFailingExecutionMoreThanOnce() {
        PipelineExecution execution =
                new PipelineExecution("pipeline-1");

        execution.fail();

        assertThrows(
                IllegalStateException.class,
                execution::fail
        );
    }
}
