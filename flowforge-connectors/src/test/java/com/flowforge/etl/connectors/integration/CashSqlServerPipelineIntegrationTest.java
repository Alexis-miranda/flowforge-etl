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


import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                                            "rejectedInputKey", "rejectedCash",
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

            // Primera ejecución: la carga debe completarse correctamente.
            PipelineExecutionResult result = executor.execute(pipeline);

            assertEquals(
                    PipelineStatus.SUCCEEDED,
                    result.status());

            // Comprobar que se han cargado únicamente los dos registros válidos.
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

                    assertEquals(
                            "ACC001-" + testId,
                            resultSet.getString("AccountCode"));
                    assertEquals(
                            "ADJ001",
                            resultSet.getString("AdjustmentId"));
                    assertEquals(
                            "EUR",
                            resultSet.getString("Currency"));
                    assertEquals(
                            0,
                            new BigDecimal("150.50").compareTo(
                                    resultSet.getBigDecimal("Amount")));

                    assertTrue(resultSet.next());

                    assertEquals(
                            "ACC002-" + testId,
                            resultSet.getString("AccountCode"));
                    assertEquals(
                            "ADJ002",
                            resultSet.getString("AdjustmentId"));
                    assertEquals(
                            "USD",
                            resultSet.getString("Currency"));
                    assertEquals(
                            0,
                            new BigDecimal("200.00").compareTo(
                                    resultSet.getBigDecimal("Amount")));

                    assertTrue(
                            !resultSet.next(),
                            "Only the two valid records should be loaded");
                }
            }


            // Comprobar que los dos registros rechazados se han guardado.
            String rejectedSql = """
                SELECT AccountCode, AdjustmentId, Currency, Amount,
                       SourceFile, RejectionReason
                FROM dbo.CashRejected
                WHERE SourceFile = ?
                ORDER BY AdjustmentId
                """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(rejectedSql)) {

                statement.setString(1, sourceFile);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());

                    assertEquals("ADJ003",
                            resultSet.getString("AdjustmentId"));
                    assertEquals("XYZ",
                            resultSet.getString("Currency"));
                    assertEquals("75.00",
                            resultSet.getString("Amount"));
                    assertEquals(sourceFile,
                            resultSet.getString("SourceFile"));
                    assertNotNull(resultSet.getString("RejectionReason"));

                    assertTrue(resultSet.next());

                    assertEquals("ADJ004",
                            resultSet.getString("AdjustmentId"));
                    assertEquals("GBP",
                            resultSet.getString("Currency"));
                    assertEquals("50.00",
                            resultSet.getString("Amount"));
                    assertEquals(sourceFile,
                            resultSet.getString("SourceFile"));
                    assertNotNull(resultSet.getString("RejectionReason"));

                    assertTrue(
                            !resultSet.next(),
                            "Exactly two rejected records should be stored");
                }
            }

            // Segunda ejecución: el mismo archivo debe rechazarse.
            PipelineExecutionResult duplicateResult =
                    executor.execute(pipeline);

            assertEquals(
                    PipelineStatus.FAILED,
                    duplicateResult.status());

            assertNotNull(duplicateResult.errorMessage());

            assertTrue(
                    duplicateResult.errorMessage()
                            .contains("already been loaded"),
                    "The duplicate load should explain why it was rejected");

            // Verificar que el intento duplicado no ha añadido más registros.
            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement("""
                             SELECT COUNT_BIG(*)
                             FROM dbo.CashStaging
                             WHERE SourceFile = ?
                             """)) {

                statement.setString(1, sourceFile);

                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(2L, resultSet.getLong(1));
                }
            }

        } finally {
            // Limpiar los datos de prueba y el registro de control de la carga.
            try (Connection connection =
                         SqlServerConnectionFactory.openConnection()) {

                // 1. Eliminar registros rechazados.
                try (PreparedStatement cleanupRejected =
                             connection.prepareStatement("""
                                 DELETE FROM dbo.CashRejected
                                 WHERE SourceFile = ?
                                 """)) {

                    cleanupRejected.setString(1, sourceFile);
                    cleanupRejected.executeUpdate();
                }

                // 2. Eliminar registros válidos de staging.
                try (PreparedStatement cleanupStaging =
                             connection.prepareStatement("""
                                 DELETE FROM dbo.CashStaging
                                 WHERE SourceFile = ?
                                 """)) {

                    cleanupStaging.setString(1, sourceFile);
                    cleanupStaging.executeUpdate();
                }

                // 3. Eliminar el registro de control de carga.
                try (PreparedStatement cleanupControl =
                             connection.prepareStatement("""
                                 DELETE FROM dbo.CashLoadControl
                                 WHERE SourceFile = ?
                                 """)) {

                    cleanupControl.setString(1, sourceFile);
                    cleanupControl.executeUpdate();
                }
            }
        }
    }
}
