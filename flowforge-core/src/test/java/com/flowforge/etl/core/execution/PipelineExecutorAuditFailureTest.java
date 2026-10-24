package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorAuditFailureTest {

    private static final String AUDIT_ERROR =
            "Simulated audit repository failure";

    @Test
    void shouldNotExecuteStepsWhenStartingAuditFails() {
        AtomicInteger executedSteps = new AtomicInteger();
        PipelineExecutionRepository repository =
                new FailingRepository(true, false, false);

        PipelineExecutor executor = createExecutor(
                repository, executedSteps);

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(emptyStepPipeline()));

        assertEquals(0, executedSteps.get());
    }

    @Test
    void shouldPropagateFailureWhenUpdatingFinalAuditFails() {
        AtomicInteger executedSteps = new AtomicInteger();
        PipelineExecutionRepository repository =
                new FailingRepository(false, true, false);

        PipelineExecutor executor = createExecutor(
                repository, executedSteps);

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(emptyStepPipeline()));

        assertEquals(1, executedSteps.get());
    }

    @Test
    void shouldPropagateFailureWhenSavingFinalAuditFails() {
        AtomicInteger executedSteps = new AtomicInteger();
        PipelineExecutionRepository repository =
                new FailingRepository(false, false, true);

        PipelineExecutor executor = createExecutor(
                repository, executedSteps);

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(emptyStepPipeline()));

        assertEquals(1, executedSteps.get());
    }

    private PipelineExecutor createExecutor(
            PipelineExecutionRepository repository,
            AtomicInteger executedSteps) {

        StepExecutor stepExecutor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    PipelineExecutionContext context) {
                executedSteps.incrementAndGet();
            }
        };

        return new PipelineExecutor(
                List.of(stepExecutor), null, repository);
    }

    private PipelineDefinition emptyStepPipeline() {
        PipelineStep step = new PipelineStep(
                "extract-step",
                "Extract test step",
                StepType.EXTRACT,
                1);

        return new PipelineDefinition(
                "audit-failure-test",
                "Audit failure test",
                "Tests audit repository failures",
                List.of(step));
    }

    private static class FailingRepository
            implements PipelineExecutionRepository {

        private final boolean failStart;
        private final boolean failUpdate;
        private final boolean failSave;

        private FailingRepository(
                boolean failStart,
                boolean failUpdate,
                boolean failSave) {
            this.failStart = failStart;
            this.failUpdate = failUpdate;
            this.failSave = failSave;
        }

        @Override
        public void start(PipelineExecutionSnapshot snapshot) {
            if (failStart) {
                throw new IllegalStateException(AUDIT_ERROR);
            }
        }

        @Override
        public void update(PipelineExecutionSnapshot snapshot) {
            if (failUpdate) {
                throw new IllegalStateException(AUDIT_ERROR);
            }
        }

        @Override
        public void save(PipelineExecutionResult result) {
            if (failSave) {
                throw new IllegalStateException(AUDIT_ERROR);
            }
        }

        @Override
        public Optional<PipelineExecutionSnapshot>
        findSnapshotByExecutionId(String executionId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecutionResult>
        findByPipelineId(String pipelineId) {
            return List.of();
        }

        @Override
        public Optional<PipelineExecutionResult>
        findByExecutionId(String executionId) {
            return Optional.empty();
        }
    }
}
