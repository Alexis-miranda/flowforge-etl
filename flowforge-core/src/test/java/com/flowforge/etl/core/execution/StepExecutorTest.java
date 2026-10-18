package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StepExecutorTest {

    @Test
    void shouldExposeSupportedStepType() {
        StepExecutor executor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                // No operation required for this contract test.
            }
        };

        assertEquals(StepType.EXTRACT, executor.supportedType());
    }

    @Test
    void shouldExecutePipelineStep() {
        StepExecutor executor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                assertEquals("extract-customers", step.id());
            }
        };

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of())
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        assertDoesNotThrow(() -> executor.execute(step, context));
    }
}
