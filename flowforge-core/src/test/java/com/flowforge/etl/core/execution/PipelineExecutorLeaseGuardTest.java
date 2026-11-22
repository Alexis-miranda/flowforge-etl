package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorLeaseGuardTest {

    @Test
    void shouldStopBeforeStartingAnotherStepWhenLeaseIsLost() {
        List<String> executed = new ArrayList<>();
        AtomicInteger checks = new AtomicInteger();
        PipelineExecutionLease lease = new PipelineExecutionLease(
                "execution-1", "worker-1", Instant.now().plusSeconds(60), 1);
        PipelineExecutionLeaseGuard guard = () -> {
            if (checks.incrementAndGet() == 4) {
                throw new PipelineExecutionLeaseLostException("execution-1");
            }
        };

        StepExecutor extract = executor(StepType.EXTRACT, executed);
        StepExecutor load = executor(StepType.LOAD, executed);
        PipelineDefinition pipeline = new PipelineDefinition(
                "pipeline-1", "Test", "", List.of(
                step("extract", StepType.EXTRACT, 1),
                step("load", StepType.LOAD, 2)));

        PipelineExecutor pipelineExecutor = new PipelineExecutor(List.of(extract, load));

        assertThrows(PipelineExecutionLeaseLostException.class,
                () -> pipelineExecutor.executeWithLease(pipeline, lease, guard));
        assertEquals(List.of("extract"), executed);
    }

    private static StepExecutor executor(StepType type, List<String> executed) {
        return new StepExecutor() {
            @Override
            public StepType supportedType() {
                return type;
            }

            @Override
            public void execute(PipelineStep step, PipelineExecutionContext context) {
                executed.add(step.id());
            }
        };
    }

    private static PipelineStep step(String id, StepType type, int order) {
        return new PipelineStep(id, id, type, order,
                new StepConfiguration(Map.of()));
    }
}
