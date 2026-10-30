package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;
import com.flowforge.etl.core.repository.InMemoryPipelineExecutionRepository;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


import static org.junit.jupiter.api.Assertions.*;

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
        assertNull(result.failedStepId());
        assertNull(result.errorMessage());
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
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
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
    void shouldUseExceptionTypeWhenFailureHasNoMessage() {
        StepExecutor failingExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                throw new IllegalStateException();
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(failingExecutor)
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals("extract", result.failedStepId());
        assertEquals("IllegalStateException", result.errorMessage());
    }

    @Test
    void shouldRejectDuplicateExecutorTypesAndConnectorKeys() {
        StepExecutor firstExecutor =
                createExecutor(StepType.EXTRACT, new ArrayList<>());

        StepExecutor secondExecutor =
                createExecutor(StepType.EXTRACT, new ArrayList<>());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutor(
                        List.of(firstExecutor, secondExecutor))
        );
    }


    @Test
    void shouldAllowMultipleExecutorsForSameTypeWithDifferentConnectors() {
        List<String> executedSteps = new ArrayList<>();

        StepExecutor csvExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.LOAD;
            }

            @Override
            public String connectorKey() {
                return "csv";
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                executedSteps.add("csv");
            }
        };

        StepExecutor sqlServerExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.LOAD;
            }

            @Override
            public String connectorKey() {
                return "sqlserver";
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                executedSteps.add("sqlserver");
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(csvExecutor, sqlServerExecutor));

        PipelineStep csvStep = new PipelineStep(
                "load-csv",
                "Load CSV",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of("connector", "csv"))
        );

        PipelineExecutionResult result = executor.execute(
                createPipeline(List.of(csvStep)));

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(List.of("csv"), executedSteps);
    }

    @Test
    void shouldFailWhenMultipleConnectorsExistAndConnectorIsMissing() {
        StepExecutor csvExecutor = createExecutorWithConnector(
                StepType.LOAD, "csv", new ArrayList<>());

        StepExecutor sqlServerExecutor = createExecutorWithConnector(
                StepType.LOAD, "sqlserver", new ArrayList<>());

        PipelineExecutor executor = new PipelineExecutor(
                List.of(csvExecutor, sqlServerExecutor));

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("load", StepType.LOAD, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals("load", result.failedStepId());
        assertTrue(result.errorMessage().contains(
                "Specify the 'connector' property"));
    }

    @Test
    void shouldFailWhenConnectorIsNotRegistered() {
        StepExecutor csvExecutor = createExecutorWithConnector(
                StepType.LOAD, "csv", new ArrayList<>());

        PipelineExecutor executor = new PipelineExecutor(
                List.of(csvExecutor));

        PipelineStep step = new PipelineStep(
                "load",
                "Load data",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of("connector", "unknown"))
        );

        PipelineExecutionResult result = executor.execute(
                createPipeline(List.of(step)));

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals("load", result.failedStepId());
        assertTrue(result.errorMessage().contains(
                "connector: unknown"));
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
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
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
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
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
                    throw new IllegalStateException("Listener failed");
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

    @Test
    void shouldReturnExecutionMetadata() {
        PipelineExecutor executor = new PipelineExecutor(List.of());

        PipelineDefinition pipeline = createPipeline(List.of());

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals("test-pipeline", result.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertTrue(
                result.executionId() != null
                        && !result.executionId().isBlank()
        );
        assertNotNull(result.startedAt());
        assertNotNull(result.finishedAt());
        assertFalse(result.duration().isNegative());
    }

    @Test
    void shouldShareContextBetweenSteps() {
        StepExecutor extractExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                context.put("records", List.of("record-1", "record-2"));
            }
        };

        StepExecutor transformExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.TRANSFORM;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {

                List<?> records = context.get("records", List.class);

                assertEquals(
                        List.of("record-1", "record-2"),
                        records
                );
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(extractExecutor, transformExecutor)
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1),
                createStep("transform", StepType.TRANSFORM, 2)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
    }

    @Test
    void shouldCreateNewContextForEachExecution() {
        List<PipelineExecutionContext> receivedContexts = new ArrayList<>();

        StepExecutor extractExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                receivedContexts.add(context);
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(extractExecutor)
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult firstResult = executor.execute(pipeline);
        PipelineExecutionResult secondResult = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, firstResult.status());
        assertEquals(PipelineStatus.SUCCEEDED, secondResult.status());
        assertEquals(2, receivedContexts.size());

        assertNotSame(receivedContexts.get(0), receivedContexts.get(1));

    }
    @Test
    void shouldRejectNullExecutorList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineExecutor(
                        (List<StepExecutor>) null
                )
        );
    }

    @Test
    void shouldSaveSuccessfulExecutionResultToRepository() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                null,
                repository
        );

        PipelineDefinition pipeline = createPipeline(List.of());

        PipelineExecutionResult result = executor.execute(pipeline);

        PipelineExecutionResult savedResult = repository
                .findByExecutionId(result.executionId())
                .orElseThrow();

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(result.executionId(), savedResult.executionId());
        assertEquals(result.pipelineId(), savedResult.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, savedResult.status());
    }



    @Test
    void shouldPersistFinalSnapshotAfterSuccessfulExecution() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                null,
                repository
        );

        PipelineDefinition pipeline = createPipeline(List.of());

        PipelineExecutionResult result = executor.execute(pipeline);

        var snapshot = repository
                .findSnapshotByExecutionId(result.executionId())
                .orElseThrow();

        assertEquals(PipelineStatus.SUCCEEDED, snapshot.status());
        assertEquals(result.executionId(), snapshot.executionId());
        assertEquals(result.pipelineId(), snapshot.pipelineId());
        assertNotNull(snapshot.startedAt());
        assertNotNull(snapshot.finishedAt());
    }

    @Test
    void shouldSaveFailedExecutionResultToRepository() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        StepExecutor failingExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                throw new IllegalStateException("Extraction failed");
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(failingExecutor),
                null,
                repository
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        PipelineExecutionResult savedResult = repository
                .findByExecutionId(result.executionId())
                .orElseThrow();

        assertEquals(PipelineStatus.FAILED, savedResult.status());
        assertEquals("extract", savedResult.failedStepId());
        assertEquals("Extraction failed", savedResult.errorMessage());
        assertEquals(result.executionId(), savedResult.executionId());
    }


    @Test
    void shouldSaveEachExecutionSeparately() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                null,
                repository
        );

        PipelineDefinition pipeline = createPipeline(List.of());

        PipelineExecutionResult firstResult = executor.execute(pipeline);
        PipelineExecutionResult secondResult = executor.execute(pipeline);

        assertNotEquals(
                firstResult.executionId(),
                secondResult.executionId()
        );

        List<PipelineExecutionResult> savedResults =
                repository.findByPipelineId(pipeline.id());

        assertEquals(2, savedResults.size());
        assertEquals(
                firstResult.executionId(),
                savedResults.get(0).executionId()
        );
        assertEquals(
                secondResult.executionId(),
                savedResults.get(1).executionId()
        );
    }


    @Test
    void shouldPersistRunningSnapshotBeforeExecutingSteps() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        List<PipelineStatus> observedStatuses = new ArrayList<>();

        PipelineExecutionRepository checkingRepository =
                new PipelineExecutionRepository() {
                    @Override
                    public void start(PipelineExecutionSnapshot snapshot) {
                        assertEquals(PipelineStatus.RUNNING, snapshot.status());
                        assertNull(snapshot.finishedAt());

                        observedStatuses.add(snapshot.status());
                        repository.start(snapshot);
                    }

                    @Override
                    public void update(PipelineExecutionSnapshot snapshot) {
                        repository.update(snapshot);
                    }

                    @Override
                    public void save(PipelineExecutionResult result) {
                        repository.save(result);
                    }

                    @Override
                    public java.util.Optional<PipelineExecutionSnapshot>
                    findSnapshotByExecutionId(String executionId) {
                        return repository.findSnapshotByExecutionId(executionId);
                    }

                    @Override
                    public List<PipelineExecutionResult> findByPipelineId(
                            String pipelineId) {
                        return repository.findByPipelineId(pipelineId);
                    }

                    @Override
                    public java.util.Optional<PipelineExecutionResult>
                    findByExecutionId(String executionId) {
                        return repository.findByExecutionId(executionId);
                    }

                    @Override
                    public List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(
                            String pipelineId) {
                        return repository.findSnapshotsByPipelineId(pipelineId);
                    }
                };

        StepExecutor checkingExecutor =
                createExecutor(StepType.EXTRACT, new ArrayList<>());

        PipelineExecutor executor = new PipelineExecutor(
                List.of(checkingExecutor),
                null,
                checkingRepository
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        PipelineExecutionSnapshot finalSnapshot =
                checkingRepository
                        .findSnapshotByExecutionId(result.executionId())
                        .orElseThrow();

        assertEquals(
                List.of(PipelineStatus.RUNNING),
                observedStatuses
        );
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(PipelineStatus.SUCCEEDED, finalSnapshot.status());
        assertEquals(result.executionId(), finalSnapshot.executionId());
        assertNotNull(finalSnapshot.startedAt());
        assertNotNull(finalSnapshot.finishedAt());
    }


    @Test
    void shouldPersistFinalSnapshotAfterFailedExecution() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        PipelineExecutor executor = new PipelineExecutor(
                List.of(),
                null,
                repository
        );

        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1)
        ));

        PipelineExecutionResult result = executor.execute(pipeline);

        PipelineExecutionSnapshot snapshot = repository
                .findSnapshotByExecutionId(result.executionId())
                .orElseThrow();

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals(PipelineStatus.FAILED, snapshot.status());
        assertEquals(result.executionId(), snapshot.executionId());
        assertEquals(result.pipelineId(), snapshot.pipelineId());
        assertNotNull(snapshot.startedAt());
        assertNotNull(snapshot.finishedAt());
        assertNotNull(snapshot.errorMessage());
    }




    @Test
    void shouldRecordSuccessfulFailedAndSkippedStepsWithCause() {
        List<String> executedSteps = new ArrayList<>();
        StepExecutor extract = createExecutor(StepType.EXTRACT, executedSteps);
        StepExecutor failingTransform = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.TRANSFORM;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                executedSteps.add(step.id());
                throw new IllegalStateException(
                        "Transformation failed",
                        new IllegalArgumentException("Invalid input data"));
            }
        };
        StepExecutor load = createExecutor(StepType.LOAD, executedSteps);

        PipelineExecutor executor = new PipelineExecutor(
                List.of(extract, failingTransform, load));
        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1),
                createStep("transform", StepType.TRANSFORM, 2),
                createStep("load", StepType.LOAD, 3)));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals(List.of("extract", "transform"), executedSteps);
        assertEquals(3, result.stepHistory().size());

        StepExecutionRecord first = result.stepHistory().get(0);
        assertEquals("extract", first.stepId());
        assertEquals(StepExecutionStatus.SUCCEEDED, first.status());
        assertNotNull(first.startedAt());
        assertNotNull(first.finishedAt());

        StepExecutionRecord failed = result.stepHistory().get(1);
        assertEquals("transform", failed.stepId());
        assertEquals(StepExecutionStatus.FAILED, failed.status());
        assertEquals(IllegalStateException.class.getName(), failed.exceptionType());
        assertEquals("Transformation failed", failed.errorMessage());
        assertEquals(IllegalArgumentException.class.getName(), failed.causeType());
        assertEquals("Invalid input data", failed.causeMessage());

        StepExecutionRecord skipped = result.stepHistory().get(2);
        assertEquals("load", skipped.stepId());
        assertEquals(StepExecutionStatus.SKIPPED, skipped.status());
        assertEquals(
                "Not executed because step 'transform' failed",
                skipped.skipReason());
        assertNull(skipped.startedAt());
        assertNotNull(skipped.finishedAt());
        assertEquals(
                "Not executed because step 'transform' failed",
                skipped.skipReason());
    }

    @Test
    void shouldReturnStepHistoryForSuccessfulPipeline() {
        List<String> executedSteps = new ArrayList<>();
        PipelineExecutor executor = new PipelineExecutor(List.of(
                createExecutor(StepType.EXTRACT, executedSteps),
                createExecutor(StepType.LOAD, executedSteps)));
        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("extract", StepType.EXTRACT, 1),
                createStep("load", StepType.LOAD, 2)));

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(2, result.stepHistory().size());
        assertTrue(result.stepHistory().stream()
                .allMatch(record -> record.status() == StepExecutionStatus.SUCCEEDED));
    }

    @Test
    void shouldPersistStepHistoryInExecutionRepository() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();
        StepExecutor failingExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.TRANSFORM;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                throw new IllegalArgumentException("Invalid transformation");
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(failingExecutor), null, repository);
        PipelineDefinition pipeline = createPipeline(List.of(
                createStep("transform", StepType.TRANSFORM, 1),
                createStep("load", StepType.LOAD, 2)));

        PipelineExecutionResult result = executor.execute(pipeline);
        PipelineExecutionResult persisted = repository
                .findByExecutionId(result.executionId())
                .orElseThrow();

        assertEquals(result.stepHistory(), persisted.stepHistory());
        assertEquals(StepExecutionStatus.FAILED, persisted.stepHistory().get(0).status());
        assertEquals(StepExecutionStatus.SKIPPED, persisted.stepHistory().get(1).status());
    }


    @Test
    void shouldNotExecutePipelineWhenInitialAuditPersistenceFails() {
        List<String> executedSteps = new ArrayList<>();
        FaultInjectingExecutionRepository repository =
                new FaultInjectingExecutionRepository(
                        PipelineExecutionPersistenceException.Operation.START);

        PipelineExecutor executor = new PipelineExecutor(
                List.of(createExecutor(StepType.EXTRACT, executedSteps)),
                null,
                repository);

        PipelineExecutionPersistenceException exception = assertThrows(
                PipelineExecutionPersistenceException.class,
                () -> executor.execute(createPipeline(List.of(
                        createStep("extract", StepType.EXTRACT, 1)))));

        assertTrue(executedSteps.isEmpty());
        assertEquals("test-pipeline", exception.pipelineId());
        assertEquals(1, exception.failures().size());
        assertEquals(
                PipelineExecutionPersistenceException.Operation.START,
                exception.failures().get(0).operation());
        assertTrue(exception.executionResult().isEmpty());
    }

    @Test
    void shouldSaveResultEvenWhenFinalSnapshotUpdateFails() {
        FaultInjectingExecutionRepository repository =
                new FaultInjectingExecutionRepository(
                        PipelineExecutionPersistenceException.Operation.UPDATE);
        PipelineExecutor executor = new PipelineExecutor(
                List.of(), null, repository);

        PipelineExecutionPersistenceException exception = assertThrows(
                PipelineExecutionPersistenceException.class,
                () -> executor.execute(createPipeline(List.of())));

        PipelineExecutionResult result =
                exception.executionResult().orElseThrow();
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(1, exception.failures().size());
        assertEquals(
                PipelineExecutionPersistenceException.Operation.UPDATE,
                exception.failures().get(0).operation());
        assertTrue(repository.delegate.findByExecutionId(result.executionId()).isPresent());
    }

    @Test
    void shouldRetainResultWhenSavingFinalResultFails() {
        FaultInjectingExecutionRepository repository =
                new FaultInjectingExecutionRepository(
                        PipelineExecutionPersistenceException.Operation.SAVE);
        PipelineExecutor executor = new PipelineExecutor(
                List.of(), null, repository);

        PipelineExecutionPersistenceException exception = assertThrows(
                PipelineExecutionPersistenceException.class,
                () -> executor.execute(createPipeline(List.of())));

        PipelineExecutionResult result =
                exception.executionResult().orElseThrow();
        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(1, exception.failures().size());
        assertEquals(
                PipelineExecutionPersistenceException.Operation.SAVE,
                exception.failures().get(0).operation());
        assertEquals(
                PipelineStatus.SUCCEEDED,
                repository.delegate
                        .findSnapshotByExecutionId(result.executionId())
                        .orElseThrow()
                        .status());
        assertTrue(repository.delegate.findByExecutionId(result.executionId()).isEmpty());
    }

    @Test
    void shouldReportBothFinalPersistenceFailuresWithoutLosingPipelineResult() {
        FaultInjectingExecutionRepository repository =
                new FaultInjectingExecutionRepository(
                        Set.of(
                                PipelineExecutionPersistenceException.Operation.UPDATE,
                                PipelineExecutionPersistenceException.Operation.SAVE));
        PipelineExecutor executor = new PipelineExecutor(
                List.of(), null, repository);

        PipelineExecutionPersistenceException exception = assertThrows(
                PipelineExecutionPersistenceException.class,
                () -> executor.execute(createPipeline(List.of())));

        assertEquals(
                List.of(
                        PipelineExecutionPersistenceException.Operation.UPDATE,
                        PipelineExecutionPersistenceException.Operation.SAVE),
                exception.failures().stream()
                        .map(PipelineExecutionPersistenceException.Failure::operation)
                        .toList());
        assertEquals(1, exception.getSuppressed().length);
        assertEquals(
                PipelineStatus.SUCCEEDED,
                exception.executionResult().orElseThrow().status());
    }


    @Test
    void shouldPreservePipelineFailureWhenAuditResultPersistenceAlsoFails() {
        FaultInjectingExecutionRepository repository =
                new FaultInjectingExecutionRepository(
                        PipelineExecutionPersistenceException.Operation.SAVE);
        StepExecutor failingExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                throw new IllegalStateException("Original ETL failure");
            }
        };
        PipelineExecutor executor = new PipelineExecutor(
                List.of(failingExecutor), null, repository);

        PipelineExecutionPersistenceException exception = assertThrows(
                PipelineExecutionPersistenceException.class,
                () -> executor.execute(createPipeline(List.of(
                        createStep("extract", StepType.EXTRACT, 1)))));

        PipelineExecutionResult originalResult =
                exception.executionResult().orElseThrow();
        assertEquals(PipelineStatus.FAILED, originalResult.status());
        assertEquals("extract", originalResult.failedStepId());
        assertEquals("Original ETL failure", originalResult.errorMessage());
        assertEquals(
                StepExecutionStatus.FAILED,
                originalResult.stepHistory().get(0).status());
        assertEquals(
                PipelineExecutionPersistenceException.Operation.SAVE,
                exception.failures().get(0).operation());
        assertEquals(
                "Simulated repository failure during SAVE",
                exception.getCause().getMessage());
    }

    private static final class FaultInjectingExecutionRepository
            implements PipelineExecutionRepository {

        private final InMemoryPipelineExecutionRepository delegate =
                new InMemoryPipelineExecutionRepository();
        private final Set<PipelineExecutionPersistenceException.Operation>
                failingOperations;

        private FaultInjectingExecutionRepository(
                PipelineExecutionPersistenceException.Operation operation) {
            this(Set.of(operation));
        }

        private FaultInjectingExecutionRepository(
                Set<PipelineExecutionPersistenceException.Operation> operations) {
            this.failingOperations = Set.copyOf(operations);
        }

        @Override
        public void start(PipelineExecutionSnapshot snapshot) {
            failIfConfigured(
                    PipelineExecutionPersistenceException.Operation.START);
            delegate.start(snapshot);
        }

        @Override
        public void update(PipelineExecutionSnapshot snapshot) {
            failIfConfigured(
                    PipelineExecutionPersistenceException.Operation.UPDATE);
            delegate.update(snapshot);
        }

        @Override
        public void save(PipelineExecutionResult result) {
            failIfConfigured(
                    PipelineExecutionPersistenceException.Operation.SAVE);
            delegate.save(result);
        }

        @Override
        public java.util.Optional<PipelineExecutionSnapshot>
        findSnapshotByExecutionId(String executionId) {
            return delegate.findSnapshotByExecutionId(executionId);
        }

        @Override
        public List<PipelineExecutionResult> findByPipelineId(String pipelineId) {
            return delegate.findByPipelineId(pipelineId);
        }

        @Override
        public java.util.Optional<PipelineExecutionResult>
        findByExecutionId(String executionId) {
            return delegate.findByExecutionId(executionId);
        }

        @Override
        public List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(
                String pipelineId) {
            return delegate.findSnapshotsByPipelineId(pipelineId);
        }

        private void failIfConfigured(
                PipelineExecutionPersistenceException.Operation operation) {
            if (failingOperations.contains(operation)) {
                throw new IllegalStateException(
                        "Simulated repository failure during " + operation);
            }
        }
    }

    private StepExecutor createExecutorWithConnector(
            StepType type,
            String connector,
            List<String> executedSteps) {

        return new StepExecutor() {
            @Override
            public StepType supportedType() {
                return type;
            }

            @Override
            public String connectorKey() {
                return connector;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                executedSteps.add(step.id());
            }
        };
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
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
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