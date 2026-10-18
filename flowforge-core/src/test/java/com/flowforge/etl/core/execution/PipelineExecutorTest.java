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
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void shouldRejectDuplicateExecutorTypes() {
        StepExecutor firstExecutor = createExecutor(
                StepType.EXTRACT, new ArrayList<>());
        StepExecutor secondExecutor = createExecutor(
                StepType.EXTRACT, new ArrayList<>());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutor(
                        List.of(firstExecutor, secondExecutor))
        );
    }

    @Test
    void shouldRejectNullPipeline() {
        PipelineExecutor executor = new PipelineExecutor(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(null)
        );
    }

    @Test
    void shouldSucceedWhenPipelineHasNoSteps() {
        PipelineExecutor executor = new PipelineExecutor(List.of());
        PipelineDefinition pipeline = createPipeline(List.of());

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals("test-pipeline", result.pipelineId());
    }

    @Test
    void shouldRejectNullExecutor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutor(
                        java.util.Arrays.asList((StepExecutor) null))
        );
    }

    @Test
    void shouldRejectExecutorWithNullSupportedType() {
        StepExecutor executor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return null;
            }

            @Override
            public void execute(PipelineStep step) {
                // No operation required for this validation test.
            }
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutor(List.of(executor))
        );
    }
    @Test
    void shouldNotifyStatusChangesWhenPipelineSucceeds() {
        List<PipelineStatus> receivedStatuses = new ArrayList<>();

        PipelineExecutionListener listener =
                (pipelineId, status) -> {
                    assertEquals("pipeline-1", pipelineId);
                    receivedStatuses.add(status);
                };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                listener
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "pipeline-1",
                "Test pipeline",
                "Pipeline used for testing",
                List.of()
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(
                List.of(
                        PipelineStatus.RUNNING,
                        PipelineStatus.SUCCEEDED
                ),
                receivedStatuses
        );
    }

    @Test
    void shouldNotifyStatusChangesWhenPipelineFails() {
        List<PipelineStatus> receivedStatuses = new ArrayList<>();

        PipelineExecutionListener listener =
                (pipelineId, status) -> receivedStatuses.add(status);

        StepExecutor extractExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(PipelineStep step) {
                throw new IllegalStateException("Extraction failed");
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(extractExecutor),
                listener
        );

        PipelineStep step = new PipelineStep(
                "extract",
                "Extract data",
                StepType.EXTRACT,
                1
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "pipeline-1",
                "Test pipeline",
                "Pipeline used for testing",
                List.of(step)
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals(
                List.of(
                        PipelineStatus.RUNNING,
                        PipelineStatus.FAILED
                ),
                receivedStatuses
        );
    }
    @Test
    void shouldContinueExecutionWhenListenerThrowsException() {
        PipelineExecutionListener listener =
                (pipelineId, status) -> {
                    throw new IllegalStateException(
                            "Listener failed");
                };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                listener
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "pipeline-1",
                "Test pipeline",
                "Pipeline used for testing",
                List.of()
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
    }

    @Test
    void shouldNotifyRunningBeforeSucceeded() {
        List<PipelineStatus> receivedStatuses = new ArrayList<>();

        PipelineExecutionListener listener =
                (pipelineId, status) -> receivedStatuses.add(status);

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                listener
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "pipeline-1",
                "Test pipeline",
                "Pipeline used for testing",
                List.of()
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(
                List.of(
                        PipelineStatus.RUNNING,
                        PipelineStatus.SUCCEEDED
                ),
                receivedStatuses
        );
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
