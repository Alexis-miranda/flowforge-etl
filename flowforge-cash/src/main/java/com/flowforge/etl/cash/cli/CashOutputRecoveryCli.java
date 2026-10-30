
package com.flowforge.etl.cash.cli;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.cash.delivery.CashOutputPublisherLoader;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retries pending FRONT output deliveries without reloading cash transactions.
 */
public final class CashOutputRecoveryCli {

    private CashOutputRecoveryCli() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 0) {
                System.err.println(
                        "Usage: CashOutputRecoveryCli (no arguments)");
                System.exit(2);
                return;
            }

            int failures = runRecovery();

            if (failures > 0) {
                System.err.println(
                        "Recovery finished with " + failures + " failure(s).");
                System.exit(1);
            }
        } catch (Exception exception) {
            System.err.println(
                    "Cash output recovery failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    static int runRecovery() throws SQLException {
        if (!SqlServerConnectionFactory.isConfigured()) {
            throw new IllegalStateException(
                    "SQL Server environment variables are not configured.");
        }

        List<String> pendingSources = findPendingSources();

        if (pendingSources.isEmpty()) {
            System.out.println("No pending FRONT output deliveries.");
            return 0;
        }

        System.out.println(
                "Found " + pendingSources.size()
                        + " pending FRONT output delivery/deliveries.");

        CashOutputPublisherLoader publisher =
                new CashOutputPublisherLoader();

        int failures = 0;

        for (String sourceFile : pendingSources) {
            try {
                PipelineStep step = new PipelineStep(
                        "recover-front-output",
                        "Recover FRONT output",
                        StepType.LOAD,
                        1,
                        new StepConfiguration(Map.of(
                                "connector", "cash-output-publisher",
                                "sourceFile", sourceFile
                        ))
                );

                publisher.execute(step, new PipelineExecutionContext());

                System.out.println("Recovered delivery: " + sourceFile);
            } catch (RuntimeException exception) {
                failures++;
                System.err.println(
                        "Could not recover " + sourceFile + ": "
                                + exception.getMessage());
            }
        }

        System.out.println(
                "Recovery summary: total=" + pendingSources.size()
                        + ", failed=" + failures
                        + ", succeeded=" + (pendingSources.size() - failures));

        return failures;
    }

    private static List<String> findPendingSources() throws SQLException {
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
        }

        return sources;
    }
}