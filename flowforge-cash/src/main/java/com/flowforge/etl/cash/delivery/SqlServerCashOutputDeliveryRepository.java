package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server implementation of the cash output delivery repository.
 */
public class SqlServerCashOutputDeliveryRepository
        implements CashOutputDeliveryRepository {

    @Override
    public List<String> findPendingSources() {
        String sql = """
                SELECT SourceFile
                FROM dbo.CashOutputDelivery
                WHERE DeliveryStatus = N'PENDING'
                ORDER BY CreatedAt, SourceFile
                """;

        List<String> sources = new ArrayList<>();

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                sources.add(result.getString("SourceFile"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not read pending output deliveries", exception);
        }

        return List.copyOf(sources);
    }

    @Override
    public CashOutputDelivery findBySourceFile(String sourceFile) {
        String sql = """
                SELECT OutputDirectory, OutputFileName, CsvPayload,
                       PayloadSha256, DeliveryStatus
                FROM dbo.CashOutputDelivery
                WHERE SourceFile = ?
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, sourceFile);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                return new CashOutputDelivery(
                        result.getString("OutputDirectory"),
                        result.getString("OutputFileName"),
                        result.getBytes("CsvPayload"),
                        result.getString("PayloadSha256"),
                        result.getString("DeliveryStatus")
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not read pending output delivery", exception);
        }
    }

    @Override
    public void markPublished(String sourceFile) {
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET DeliveryStatus = N'PUBLISHED',
                    PublishedAt = SYSUTCDATETIME(),
                    LastError = NULL
                WHERE SourceFile = ?
                  AND DeliveryStatus = N'PENDING'
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, sourceFile);
            int updated = statement.executeUpdate();

            if (updated != 1) {
                throw new IllegalStateException(
                        "Delivery was not pending when publication completed: "
                                + sourceFile);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "File was published but SQL delivery status could not be updated",
                    exception);
        }
    }

    @Override
    public void recordError(String sourceFile, String message) {
        String sql = """
                UPDATE dbo.CashOutputDelivery
                SET LastError = ?
                WHERE SourceFile = ?
                  AND DeliveryStatus = N'PENDING'
                """;

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            String safeMessage = message == null
                    ? "Unknown publication error"
                    : message;

            statement.setString(1,
                    safeMessage.length() > 1000
                            ? safeMessage.substring(0, 1000)
                            : safeMessage);
            statement.setString(2, sourceFile);
            statement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println(
                    "Could not record delivery error: "
                            + exception.getMessage());
        }
    }
}
