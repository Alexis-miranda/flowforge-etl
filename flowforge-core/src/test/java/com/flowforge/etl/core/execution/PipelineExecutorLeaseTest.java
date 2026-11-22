package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorLeaseTest {

    @Test
    void shouldExposeAcquiredLeaseToEveryPipelineStep() {
        PipelineExecutionLease lease = new PipelineExecutionLease(
                "execution-123",
                "worker-1",
                Instant.parse("2026-11-22T12:05:00Z"),
                7L);
        AtomicReference<PipelineExecutionLease> observedLease =
                new AtomicReference<>();

        StepExecutor executor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                observedLease.set(context.executionLease().orElseThrow());
            }
        };

        PipelineExecutor pipelineExecutor = new PipelineExecutor(List.of(executor));
        PipelineExecutionResult result = pipelineExecutor.executeWithLease(
                pipelineWithOneExtractStep(), lease);

        assertEquals("execution-123", result.executionId());
        assertEquals(lease, observedLease.get());
        assertEquals(7L, observedLease.get().fencingToken());
    }

    @Test
    void shouldLeaveLeaseAbsentForLocallyManagedExecution() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertEquals(Optional.empty(), context.executionLease());
    }

    @Test
    void shouldRejectMissingLeaseBeforeStartingExecution() {
        PipelineExecutor executor = new PipelineExecutor(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.executeWithLease(
                        pipelineWithOneExtractStep(), null));
    }

    private static PipelineDefinition pipelineWithOneExtractStep() {
        PipelineStep step = new PipelineStep(
                "extract",
                "Extract",
                StepType.EXTRACT,
                1);
        return new PipelineDefinition(
                "lease-pipeline",
                "Lease pipeline",
                "Pipeline used to verify lease propagation",
                List.of(step));
    }
}