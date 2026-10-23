package com.flowforge.etl.connectors.integration;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerCashStagingLoader;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.file.csv.CsvExtractor;
import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;
import com.flowforge.etl.core.transform.CashValidator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashSqlServerPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadOnlyValidCashRecordsIntoSqlServer() throws Exception {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration skipped: configure FLOWFORGE_DB_JDBC_URL, "
                        + "FLOWFORGE_DB_USER and FLOWFORGE_DB_PASSWORD");

        String testId = UUID.randomUUID().toString();
        String sourceFile = "cash-e2e-" + testId + ".csv";
        Path inputFile = tempDir.resolve(sourceFile);

        Files.writeString(
                inputFile,
                "accountCode,adjustmentId,currency,amount\n"
                        + "ACC001-" + testId + ",ADJ001,EUR,150.50\n"
                        + "ACC002-" + testId + ",ADJ002,USD,200.00\n"
                        + "ACC003-" + testId + ",ADJ003,XYZ,75.00\n"
                        + ",ADJ004,GBP,50.00\n",
                StandardCharsets.UTF_8);

        String cleanupSql = """
                DELETE FROM dbo.CashStaging
                WHERE SourceFile = ?
                """;

        try {
            PipelineDefinition pipeline = new PipelineDefinition(
                    "cash-sqlserver-e2e-" + testId,
                    "Cash SQL Server End-to-End",
                    "Extract, validate and load valid cash records",
                    List.of(
                            new PipelineStep(
                                    "extract-cash",
                                    "Extract cash CSV",
                                    StepType.EXTRACT,
                                    1,
                                    new StepConfiguration(Map.of(
                                            "path", inputFile.toString(),
                                            "outputKey", "cashRecords",
                                            "requiredHeaders",
                                            "accountCode,adjustmentId,currency,amount"
                                    ))
                            ),
                            new PipelineStep(
                                    "validate-cash",
                                    "Validate cash records",
                                    StepType.TRANSFORM,
                                    2,
                                    new StepConfiguration(Map.of(
                                            "connector", "cash-validator",
                                            "inputKey", "cashRecords",
                                            "validOutputKey", "validCash",
                                            "rejectedOutputKey", "rejectedCash"
                                    ))
                            ),
                            new PipelineStep(
                                    "load-valid-cash",
                                    "Load valid records into SQL Server",
                                    StepType.LOAD,
                                    3,
                                    new StepConfiguration(Map.of(
                                            "connector", "sqlserver",
                                            "inputKey", "validCash",
                                            "sourceFile", sourceFile
                                    ))
                            )
                    )
            );

            List<StepExecutor> executors = List.of(
                    new CsvExtractor(),
                    new CashValidator(),
                    new SqlServerCashStagingLoader()
            );

            PipelineExecutor executor = new PipelineExecutor(executors);
            PipelineExecutionResult result = executor.execute(pipeline);

            assertEquals(PipelineStatus.SUCCEEDED, result.status());

            String querySql = """
                    SELECT AccountCode, AdjustmentId, Currency, Amount
                    FROM dbo.CashStaging
                    WHERE SourceFile = ?
                    ORDER BY AccountCode
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(querySql)) {

                statement.setString(1, sourceFile);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("ACC001-" + testId,
                            resultSet.getString("AccountCode"));
                    assertEquals("ADJ001",
                            resultSet.getString("AdjustmentId"));
                    assertEquals("EUR",
                            resultSet.getString("Currency"));
                    assertEquals("150.50",
                            resultSet.getString("Amount"));

                    assertTrue(resultSet.next());
                    assertEquals("ACC002-" + testId,
                            resultSet.getString("AccountCode"));
                    assertEquals("ADJ002",
                            resultSet.getString("AdjustmentId"));
                    assertEquals("USD",
                            resultSet.getString("Currency"));
                    assertEquals("200.00",
                            resultSet.getString("Amount"));

                    assertTrue(
                            !resultSet.next(),
                            "Only the two valid records should be loaded");
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
