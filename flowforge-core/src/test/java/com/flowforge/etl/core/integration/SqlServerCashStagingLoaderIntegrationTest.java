
package com.flowforge.etl.core.integration;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.load.SqlServerCashStagingLoader;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "FLOWFORGE_DB_PASSWORD must be configured");
        }

        String testId = UUID.randomUUID().toString();
        String accountCode = "TEST-" + testId;
        String sourceFile = "integration-test-" + testId + ".csv";

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

        String sql = """
                SELECT AccountCode, AdjustmentId, Currency, Amount, SourceFile
                FROM dbo.CashStaging
                WHERE AccountCode = ? AND SourceFile = ?
                """;

        try (Connection connection =
                     DriverManager.getConnection(JDBC_URL, user, password);
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, accountCode);
            statement.setString(2, sourceFile);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertEquals(true, resultSet.next());
                assertEquals(accountCode, resultSet.getString("AccountCode"));
                assertEquals("ADJ-TEST", resultSet.getString("AdjustmentId"));
                assertEquals("EUR", resultSet.getString("Currency"));
                assertEquals("123.45", resultSet.getString("Amount"));
                assertEquals(sourceFile, resultSet.getString("SourceFile"));
            }
        }
    }
}
