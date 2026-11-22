package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL Server implementation of the cash output delivery repository. */
public class SqlServerCashOutputDeliveryRepository implements CashOutputDeliveryRepository {

    @Override
    public List<String> findPendingSources() {
        String sql = """
                SELECT SourceFile FROM dbo.CashOutputDelivery
                WHERE DeliveryStatus = N'PENDING'
                ORDER BY CreatedAt, SourceFile
                """;
        List<String> sources = new ArrayList<>();
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) sources.add(result.getString("SourceFile"));
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read pending output deliveries", exception);
        }
        return List.copyOf(sources);
    }

    @Override
    public CashOutputDelivery findBySourceFile(String sourceFile) {
        String sql = """
                SELECT OutputDirectory, OutputFileName, CsvPayload, PayloadSha256,
                       DeliveryStatus, PublishedPath, PublishedSha256, PublishedExecutionId
                FROM dbo.CashOutputDelivery WHERE SourceFile = ?
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceFile);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new CashOutputDelivery(
                        result.getString("OutputDirectory"),
                        result.getString("OutputFileName"),
                        result.getBytes("CsvPayload"),
                        result.getString("PayloadSha256"),
                        result.getString("DeliveryStatus"),
                        result.getString("PublishedPath"),
                        result.getString("PublishedSha256"),
                        result.getString("PublishedExecutionId"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read output delivery", exception);
        }
    }

    @Override
    public void markPublished(String sourceFile) {
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET DeliveryStatus = N'PUBLISHED', PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL
                WHERE SourceFile = ? AND DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceFile);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Delivery was not pending when publication completed: " + sourceFile);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "File was published but SQL delivery status could not be updated", exception);
        }
    }

    @Override
    public boolean markPublished(String sourceFile, String executionId,
                                 String publishedPath, String publishedSha256) {
        validateArtifact(executionId, publishedPath, publishedSha256);
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET DeliveryStatus = N'PUBLISHED', PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL, PublishedPath = ?, PublishedSha256 = ?,
                    PublishedExecutionId = ?
                WHERE SourceFile = ? AND DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindArtifact(statement, publishedPath, publishedSha256, executionId, sourceFile);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Artifact was written but SQL delivery reference could not be updated",
                    exception);
        }
    }

    @Override
    public boolean markPublished(String sourceFile, PipelineExecutionLease lease) {
        validateLease(lease);
        String sql = """
                UPDATE delivery
                SET DeliveryStatus = N'PUBLISHED', PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL
                FROM dbo.CashOutputDelivery AS delivery
                INNER JOIN dbo.ETLExecutionLease AS executionLease
                    ON executionLease.ExecutionId = ?
                   AND executionLease.OwnerId = ?
                   AND executionLease.FencingToken = ?
                   AND executionLease.LeaseUntil > SYSUTCDATETIME()
                WHERE delivery.SourceFile = ? AND delivery.DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLease(statement, lease, 0);
            statement.setString(4, sourceFile);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Fenced SQL delivery status update failed", exception);
        }
    }

    @Override
    public boolean markPublished(String sourceFile, PipelineExecutionLease lease,
                                 String publishedPath, String publishedSha256) {
        validateLease(lease);
        validateArtifact(lease.executionId(), publishedPath, publishedSha256);
        String sql = """
                UPDATE delivery
                SET DeliveryStatus = N'PUBLISHED', PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL, PublishedPath = ?, PublishedSha256 = ?,
                    PublishedExecutionId = ?
                FROM dbo.CashOutputDelivery AS delivery
                INNER JOIN dbo.ETLExecutionLease AS executionLease
                    ON executionLease.ExecutionId = ?
                   AND executionLease.OwnerId = ?
                   AND executionLease.FencingToken = ?
                   AND executionLease.LeaseUntil > SYSUTCDATETIME()
                WHERE delivery.SourceFile = ? AND delivery.DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, publishedPath);
            statement.setString(2, publishedSha256);
            statement.setString(3, lease.executionId());
            bindLease(statement, lease, 3);
            statement.setString(7, sourceFile);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Fenced SQL delivery artifact reference update failed", exception);
        }
    }

    @Override
    public void recordError(String sourceFile, String message) {
        String sql = """
                UPDATE dbo.CashOutputDelivery SET LastError = ?
                WHERE SourceFile = ? AND DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeMessage(message));
            statement.setString(2, sourceFile);
            statement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println("Could not record delivery error: " + exception.getMessage());
        }
    }

    @Override
    public boolean recordError(String sourceFile, String message, PipelineExecutionLease lease) {
        validateLease(lease);
        String sql = """
                UPDATE delivery SET LastError = ?
                FROM dbo.CashOutputDelivery AS delivery
                INNER JOIN dbo.ETLExecutionLease AS executionLease
                    ON executionLease.ExecutionId = ?
                   AND executionLease.OwnerId = ?
                   AND executionLease.FencingToken = ?
                   AND executionLease.LeaseUntil > SYSUTCDATETIME()
                WHERE delivery.SourceFile = ? AND delivery.DeliveryStatus = N'PENDING'
                """;
        try (Connection connection = SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safeMessage(message));
            bindLease(statement, lease, 1);
            statement.setString(5, sourceFile);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            System.err.println("Could not record fenced delivery error: " + exception.getMessage());
            return false;
        }
    }

    private static void bindArtifact(PreparedStatement statement, String path, String hash,
                                     String executionId, String sourceFile) throws SQLException {
        statement.setString(1, path);
        statement.setString(2, hash);
        statement.setString(3, executionId);
        statement.setString(4, sourceFile);
    }

    private static void bindLease(PreparedStatement statement, PipelineExecutionLease lease,
                                  int offset) throws SQLException {
        statement.setString(1 + offset, lease.executionId());
        statement.setString(2 + offset, lease.ownerId());
        statement.setLong(3 + offset, lease.fencingToken());
    }

    private static void validateArtifact(String executionId, String path, String hash) {
        if (executionId == null || executionId.isBlank()
                || path == null || path.isBlank()
                || hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Published artifact metadata is invalid");
        }
    }

    private static void validateLease(PipelineExecutionLease lease) {
        if (lease == null) throw new IllegalArgumentException("Execution lease must not be null");
    }

    private static String safeMessage(String message) {
        String value = message == null ? "Unknown publication error" : message;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}