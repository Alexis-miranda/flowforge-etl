
package com.flowforge.etl.connectors.integration;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRepository;
import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorAuditIntegrationTest {


    @Test
    void shouldPersistFailedExecutionCreatedByFactory() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server environment variables are not configured"
        );

        String pipelineId = "factory-audit-failed-" + UUID.randomUUID();
        String failedStepId = "unsupported-load-step";

        PipelineDefinition pipeline = new PipelineDefinition(
                pipelineId,
                "Audit failure integration test",
                "Verifies persistence of failed executions",
                List.of(
                        new PipelineStep(
                                failedStepId,
                                "Step without registered executor",
                                StepType.LOAD,
                                1
                        )
                )
        );

        PipelineExecutor executor = PipelineExecutorFactory.create();

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.FAILED, result.status());
        assertEquals(pipelineId, result.pipelineId());
        assertEquals(failedStepId, result.failedStepId());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().isBlank());
        assertNotNull(result.finishedAt());

        SqlServerPipelineExecutionRepository repository =
                new SqlServerPipelineExecutionRepository();

        PipelineExecutionSnapshot snapshot =
                repository.findSnapshotByExecutionId(result.executionId())
                        .orElseThrow(
                                () -> new AssertionError(
                                        "Failed execution was not persisted"
                                )
                        );

        assertEquals(result.executionId(), snapshot.executionId());
        assertEquals(pipelineId, snapshot.pipelineId());
        assertEquals(PipelineStatus.FAILED, snapshot.status());
        assertEquals(failedStepId, snapshot.failedStepId());
        assertEquals(result.errorMessage(), snapshot.errorMessage());
        assertNotNull(snapshot.startedAt());
        assertNotNull(snapshot.finishedAt());
        assertTrue(
                !snapshot.finishedAt().isBefore(snapshot.startedAt()),
                "FinishedAt must not precede StartedAt"
        );
    }

    @Test
    void shouldPersistSuccessfulExecutionCreatedByFactory() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server environment variables are not configured"
        );

        String pipelineId = "factory-audit-" + UUID.randomUUID();

        PipelineDefinition pipeline = new PipelineDefinition(
                pipelineId,
                "Audit integration test",
                "Verifies executor factory audit persistence",
                List.of()
        );

        PipelineExecutor executor = PipelineExecutorFactory.create();

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(pipelineId, result.pipelineId());
        assertNotNull(result.executionId());
        assertNotNull(result.finishedAt());

        SqlServerPipelineExecutionRepository repository =
                new SqlServerPipelineExecutionRepository();

        Optional<PipelineExecutionSnapshot> snapshotOptional =
                repository.findSnapshotByExecutionId(result.executionId());

        assertTrue(
                snapshotOptional.isPresent(),
                "Execution must be present in SQL Server"
        );

        PipelineExecutionSnapshot snapshot = snapshotOptional.orElseThrow();

        assertEquals(result.executionId(), snapshot.executionId());
        assertEquals(pipelineId, snapshot.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, snapshot.status());
        assertNotNull(snapshot.startedAt());
        assertNotNull(snapshot.finishedAt());
    }
}
