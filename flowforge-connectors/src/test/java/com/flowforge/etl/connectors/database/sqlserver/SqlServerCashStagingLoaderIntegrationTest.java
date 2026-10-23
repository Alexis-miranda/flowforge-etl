
package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerCashStagingLoaderIntegrationTest {

    @Test
    void shouldInsertCashRecordIntoSqlServerStaging() throws Exception {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration skipped: configure FLOWFORGE_DB_JDBC_URL, "
                        + "FLOWFORGE_DB_USER and FLOWFORGE_DB_PASSWORD");

        String testId = UUID.randomUUID().toString();
        String accountCode = "TEST-" + testId;
        String sourceFile = "integration-test-" + testId + ".csv";

        String cleanupSql = """
                DELETE FROM dbo.CashStaging
                WHERE SourceFile = ?
                """;

        try {
            PipelineExecutionContext context =
                    new PipelineExecutionContext();

            context.put("cashRecords", List.of(Map.of(
                    "accountCode", accountCode,
                    "adjustmentId", "ADJ-TEST",
                    "currency", "EUR",
                    "amount", "123.45"
            )));

            PipelineStep step = new PipelineStep(
                    "load-sqlserver",
                    "Load test record into SQL Server",
                    StepType.LOAD,
                    1,
                    new StepConfiguration(Map.of(
                            "connector", "sqlserver",
                            "inputKey", "cashRecords",
                            "sourceFile", sourceFile
                    ))
            );

            new SqlServerCashStagingLoader().execute(step, context);

            String querySql = """
                    SELECT AccountCode, AdjustmentId, Currency, Amount, SourceFile
                    FROM dbo.CashStaging
                    WHERE AccountCode = ? AND SourceFile = ?
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(querySql)) {

                statement.setString(1, accountCode);
                statement.setString(2, sourceFile);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(
                            resultSet.next(),
                            "Expected the cash record to be inserted");

                    assertEquals(accountCode,
                            resultSet.getString("AccountCode"));
                    assertEquals("ADJ-TEST",
                            resultSet.getString("AdjustmentId"));
                    assertEquals("EUR",
                            resultSet.getString("Currency"));
                    assertEquals("123.45",
                            resultSet.getString("Amount"));
                    assertEquals(sourceFile,
                            resultSet.getString("SourceFile"));

                    assertTrue(
                            !resultSet.next(),
                            "Expected exactly one matching record");
                }
            }
        } finally {
            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement cleanup =
                         connection.prepareStatement(cleanupSql)) {

                cleanup.setString(1, sourceFile);
                cleanup.executeUpdate();
            }
        }
    }
}
