package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.execution.StepExecutionRecord;
import com.flowforge.etl.core.execution.StepExecutionStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerPipelineStepExecutionRepositoryIntegrationTest {

    @Test
    void shouldPersistRunningAndCompletedStepLifecycle() throws Exception {
        Assumptions.assumeTrue(SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        String executionId = UUID.randomUUID().toString();
        String stepId = "step-" + UUID.randomUUID();
        Instant startedAt = Instant.now();
        SqlServerPipelineExecutionRepository repository =
                new SqlServerPipelineExecutionRepository();

        try {
            insertRunningExecution(executionId);
            repository.startStep(executionId, stepId, "Integration step", startedAt);

            try (Connection connection = SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT Status, StartedAt, FinishedAt
                         FROM dbo.ETLStepExecution
                         WHERE ExecutionId = ? AND StepId = ?
                         """)) {
                statement.setString(1, executionId);
                statement.setString(2, stepId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("RUNNING", result.getString("Status"));
                    assertNull(result.getTimestamp("FinishedAt"));
                }
            }

            Instant finishedAt = Instant.now();
            repository.finishStep(executionId, new StepExecutionRecord(
                    stepId, "Integration step", StepExecutionStatus.SUCCEEDED,
                    startedAt, finishedAt, null, null, null, null, null));

            try (Connection connection = SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT Status, StartedAt, FinishedAt
                         FROM dbo.ETLStepExecution
                         WHERE ExecutionId = ? AND StepId = ?
                         """)) {
                statement.setString(1, executionId);
                statement.setString(2, stepId);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("SUCCEEDED", result.getString("Status"));
                    assertEquals(finishedAt, result.getTimestamp("FinishedAt").toInstant());
                }
            }
        } finally {
            deleteTestRows(executionId);
        }
    }

    private static void insertRunningExecution(String executionId) throws Exception {
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dbo.ETLExecution
                         (ExecutionId, PipelineId, Status, StartedAt)
                     VALUES (?, N'step-audit-integration', N'RUNNING', SYSUTCDATETIME())
                     """)) {
            statement.setString(1, executionId);
            statement.executeUpdate();
        }
    }

    private static void deleteTestRows(String executionId) {
        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            try (PreparedStatement steps = connection.prepareStatement(
                    "DELETE FROM dbo.ETLStepExecution WHERE ExecutionId = ?")) {
                steps.setString(1, executionId);
                steps.executeUpdate();
            }
            try (PreparedStatement executions = connection.prepareStatement(
                    "DELETE FROM dbo.ETLExecution WHERE ExecutionId = ?")) {
                executions.setString(1, executionId);
                executions.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not clean up step audit integration test",
                    exception);
        }
    }
}