package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineExecutorTest {

    @Test
    void shouldExecuteStepsInOrder() {
        List<String> executedSteps = new ArrayList<>();

        StepExecutor extractExecutor = createExecutor(
                StepType.EXTRACT, executedSteps);
        StepExecutor transformExecutor = createExecutor(
                StepType.TRANSFORM, executedSteps);

        PipelineExecutor executor = new PipelineExecutor(
                List.of(extractExecutor, transformExecutor));

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("transform", StepType.TRANSFORM, 2),
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals("test-pipeline", result.pipelineId());
        assertEquals(null, result.failedStepId());
        assertEquals(null, result.errorMessage());
        assertEquals(List.of("extract", "transform"), executedSteps);
    }

    @Test
    void shouldReturnSucceededWhenAllStepsComplete() {
        List<String> executedSteps = new ArrayList<>();

        PipelineExecutor executor = new PipelineExecutor(List.of(
                createExecutor(StepType.EXTRACT, executedSteps)
        ));

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals("test-pipeline", result.pipelineId());
    }

    @Test
    void shouldReturnFailedWhenNoExecutorSupportsStepType() {
        PipelineExecutor executor = new PipelineExecutor(List.of());

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals("extract", result.failedStepId());
        assertEquals(
                "No executor registered for step type: EXTRACT",
                result.errorMessage()
        );
    }

    @Test
    void shouldStopExecutingWhenStepFails() {
        List<String> executedSteps = new ArrayList<>();

        StepExecutor failingExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(PipelineStep step) {
                executedSteps.add(step.id());
                throw new IllegalStateException("Extraction failed");
            }
        };

        StepExecutor transformExecutor = createExecutor(
                StepType.TRANSFORM, executedSteps);

        PipelineExecutor executor = new PipelineExecutor(
                List.of(failingExecutor, transformExecutor));

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1),
                createStep("transform", StepType.TRANSFORM, 2)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals("extract", result.failedStepId());
        assertEquals("Extraction failed", result.errorMessage());
        assertEquals(List.of("extract"), executedSteps);
    }

    private StepExecutor createExecutor(
            StepType type,
            List<String> executedSteps) {

        return new StepExecutor() {
            @Override
            public StepType supportedType() {
                return type;
            }

            @Override
            public void execute(PipelineStep step) {
                executedSteps.add(step.id());
            }
        };
    }

    private PipelineStep createStep(
            String id,
            StepType type,
            int order) {

        return new PipelineStep(
                id,
                id,
                type,
                order,
                new StepConfiguration(Map.of())
        );
    }

    private PipelineDefinition createPipeline(
            List<PipelineStep> steps) {

        return new PipelineDefinition(
                "test-pipeline",
                "Test pipeline",
                "Pipeline used for unit tests",
                steps
        );
    }
}
