
package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerPipelineExecutionRepositoryIntegrationTest {

    @Test
    void shouldPersistAndUpdatePipelineExecutionAudit() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionRepository repository =
                new SqlServerPipelineExecutionRepository();

        String executionId = UUID.randomUUID().toString();
        String pipelineId = "audit-integration-" + executionId;

        Instant startedAt = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        PipelineExecutionSnapshot running =
                new PipelineExecutionSnapshot(
                        executionId,
                        pipelineId,
                        PipelineStatus.RUNNING,
                        null,
                        null,
                        startedAt,
                        null);

        // Insert the initial RUNNING record.
        repository.start(running);

        PipelineExecutionSnapshot persistedRunning =
                repository.findSnapshotByExecutionId(executionId)
                        .orElseThrow(() ->
                                new AssertionError(
                                        "RUNNING audit record was not found"));

        assertEquals(executionId, persistedRunning.executionId());
        assertEquals(pipelineId, persistedRunning.pipelineId());
        assertEquals(PipelineStatus.RUNNING, persistedRunning.status());
        assertEquals(startedAt, persistedRunning.startedAt());
        assertEquals(null, persistedRunning.finishedAt());

        Instant finishedAt = Instant.now()
                .truncatedTo(ChronoUnit.MICROS);

        PipelineExecutionSnapshot succeeded =
                new PipelineExecutionSnapshot(
                        executionId,
                        pipelineId,
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        startedAt,
                        finishedAt);

        // Update the same row to its final status.
        repository.update(succeeded);

        PipelineExecutionResult result =
                new PipelineExecutionResult(
                        executionId,
                        pipelineId,
                        PipelineStatus.SUCCEEDED,
                        null,
                        null,
                        startedAt,
                        finishedAt);

        // Verify that the final result is already persisted.
        repository.save(result);

        PipelineExecutionSnapshot persistedFinal =
                repository.findSnapshotByExecutionId(executionId)
                        .orElseThrow(() ->
                                new AssertionError(
                                        "Final audit record was not found"));

        assertEquals(executionId, persistedFinal.executionId());
        assertEquals(pipelineId, persistedFinal.pipelineId());
        assertEquals(PipelineStatus.SUCCEEDED, persistedFinal.status());
        assertEquals(startedAt, persistedFinal.startedAt());
        assertEquals(finishedAt, persistedFinal.finishedAt());
        assertTrue(persistedFinal.finished());

        PipelineExecutionResult persistedResult =
                repository.findByExecutionId(executionId)
                        .orElseThrow(() ->
                                new AssertionError(
                                        "Execution result was not found"));

        assertEquals(executionId, persistedResult.executionId());
        assertEquals(PipelineStatus.SUCCEEDED, persistedResult.status());
        assertEquals(finishedAt, persistedResult.finishedAt());

        assertNotNull(persistedResult.duration());
    }
}
