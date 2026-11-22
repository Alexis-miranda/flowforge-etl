package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionLeaseRepository;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerCashOutputDeliveryRepositoryIntegrationTest {

    @Test
    void shouldRejectStaleLeaseAndPersistOnlyCurrentArtifactReference() throws Exception {
        Assumptions.assumeTrue(SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        String executionId = UUID.randomUUID().toString();
        String sourceFile = "lease-fence-" + UUID.randomUUID() + ".csv";
        byte[] payload = "accountCode,amount\nACC001,10.00\n".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload));
        String stalePath = "/tmp/stale-artifact.csv";
        String currentPath = "/tmp/current-artifact.csv";

        insertRunningExecution(executionId);
        insertPendingDelivery(sourceFile, payload, hash);
        SqlServerPipelineExecutionLeaseRepository leases =
                new SqlServerPipelineExecutionLeaseRepository();
        SqlServerCashOutputDeliveryRepository deliveries =
                new SqlServerCashOutputDeliveryRepository();

        try {
            PipelineExecutionLease staleLease = leases.tryAcquire(
                    executionId, "worker-old", Duration.ofSeconds(30)).orElseThrow();
            assertTrue(leases.release(staleLease));
            PipelineExecutionLease currentLease = leases.tryAcquire(
                    executionId, "worker-current", Duration.ofSeconds(30)).orElseThrow();
            assertTrue(currentLease.fencingToken() > staleLease.fencingToken());

            assertFalse(deliveries.markPublished(
                    sourceFile, staleLease, stalePath, hash));
            assertFalse(deliveries.recordError(sourceFile, "stale worker error", staleLease));
            assertTrue(deliveries.markPublished(
                    sourceFile, currentLease, currentPath, hash));

            try (Connection connection = SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT DeliveryStatus, LastError, PublishedPath,
                                PublishedSha256, PublishedExecutionId
                         FROM dbo.CashOutputDelivery WHERE SourceFile = ?
                         """)) {
                statement.setString(1, sourceFile);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("PUBLISHED", result.getString("DeliveryStatus"));
                    assertNull(result.getString("LastError"));
                    assertEquals(currentPath, result.getString("PublishedPath"));
                    assertEquals(hash, result.getString("PublishedSha256").trim());
                    assertEquals(executionId, result.getString("PublishedExecutionId"));
                }
            }
        } finally {
            deleteTestRows(sourceFile, executionId);
        }
    }

    private static void insertRunningExecution(String executionId) throws Exception {
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dbo.ETLExecution (ExecutionId, PipelineId, Status, StartedAt)
                     VALUES (?, ?, N'RUNNING', SYSUTCDATETIME())
                     """)) {
            statement.setString(1, executionId);
            statement.setString(2, "cash-lease-fence-integration");
            statement.executeUpdate();
        }
    }

    private static void insertPendingDelivery(String sourceFile, byte[] payload, String hash)
            throws Exception {
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dbo.CashOutputDelivery
                         (SourceFile, BackOfficeCode, OutputFileName, OutputDirectory,
                          CsvPayload, PayloadSha256, DeliveryStatus)
                     VALUES (?, N'TEST', N'fenced-output.csv', N'.', ?, ?, N'PENDING')
                     """)) {
            statement.setString(1, sourceFile);
            statement.setBytes(2, payload);
            statement.setString(3, hash);
            statement.executeUpdate();
        }
    }

    private static void deleteTestRows(String sourceFile, String executionId) {
        try (Connection connection = SqlServerConnectionFactory.openConnection()) {
            try (PreparedStatement delivery = connection.prepareStatement(
                    "DELETE FROM dbo.CashOutputDelivery WHERE SourceFile = ?")) {
                delivery.setString(1, sourceFile);
                delivery.executeUpdate();
            }
            try (PreparedStatement lease = connection.prepareStatement(
                    "DELETE FROM dbo.ETLExecutionLease WHERE ExecutionId = ?")) {
                lease.setString(1, executionId);
                lease.executeUpdate();
            }
            try (PreparedStatement execution = connection.prepareStatement(
                    "DELETE FROM dbo.ETLExecution WHERE ExecutionId = ?")) {
                execution.setString(1, executionId);
                execution.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not clean up cash lease fencing integration test", exception);
        }
    }
}