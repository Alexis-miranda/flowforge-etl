
package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServerCashStagingLoaderIntegrationTest {

    private static final String JDBC_URL =
            "jdbc:sqlserver://localhost;"
                    + "instanceName=MSSQLSERVER01;"
                    + "databaseName=FlowForgeETL_Dev;"
                    + "encrypt=true;"
                    + "trustServerCertificate=true;";

    @Test
    @EnabledIfEnvironmentVariable(
            named = "FLOWFORGE_DB_USER",
            matches = ".+")
    void shouldInsertCashRecordIntoSqlServerStaging() throws Exception {
        String user = System.getenv("FLOWFORGE_DB_USER");
        String password = System.getenv("FLOWFORGE_DB_PASSWORD");

        Assumptions.assumeTrue(
                password != null && !password.isBlank(),
                "FLOWFORGE_DB_PASSWORD is not configured");

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
                         DriverManager.getConnection(JDBC_URL, user, password);
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
                         DriverManager.getConnection(JDBC_URL, user, password);
                 PreparedStatement cleanup =
                         connection.prepareStatement(cleanupSql)) {

                cleanup.setString(1, sourceFile);
                cleanup.executeUpdate();
            }
        }
    }
}
