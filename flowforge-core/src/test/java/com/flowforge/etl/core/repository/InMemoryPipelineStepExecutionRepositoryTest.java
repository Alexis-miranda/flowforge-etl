package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PersistedStepExecutionStatus;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.execution.StepExecutionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPipelineStepExecutionRepositoryTest {

    @Test
    void shouldExposeRunningAndTerminalStepStatesForRecoveryAssessment() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();
        Instant executionStartedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant stepStartedAt = executionStartedAt.plusSeconds(1);
        Instant stepFinishedAt = stepStartedAt.plusSeconds(2);

        repository.start(new PipelineExecutionSnapshot(
                "execution-1", "pipeline-1", PipelineStatus.RUNNING,
                null, null, executionStartedAt, null));
        repository.startStep("execution-1", "extract", "Extract", stepStartedAt);

        assertEquals(PersistedStepExecutionStatus.RUNNING,
                repository.findStepExecutions("execution-1").get(0).status());

        repository.finishStep("execution-1", new StepExecutionRecord(
                "extract", "Extract", StepExecutionStatus.SUCCEEDED,
                stepStartedAt, stepFinishedAt,
                null, null, null, null, null));

        assertEquals(List.of(PersistedStepExecutionStatus.SUCCEEDED),
                repository.findStepExecutions("execution-1").stream()
                        .map(step -> step.status()).toList());
    }
}
