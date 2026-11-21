package com.flowforge.etl.cash.execution;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.cash.validation.CashValidator;

import com.flowforge.etl.cash.persistence.SqlServerCashStagingLoader;
import com.flowforge.etl.cash.delivery.CashFrontCsvLoader;
import com.flowforge.etl.cash.delivery.CashFrontCsvPrepareLoader;
import com.flowforge.etl.cash.delivery.CashOutputPublisherLoader;
import com.flowforge.etl.connectors.file.csv.CsvExtractor;
import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashSqlServerPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoadCashPreserveOriginalRowsAndGenerateFrontCsv()
            throws Exception {

        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration skipped: configure "
                        + "FLOWFORGE_DB_JDBC_URL, FLOWFORGE_DB_USER "
                        + "and FLOWFORGE_DB_PASSWORD"
        );

        String testId = UUID.randomUUID().toString();
        String sourceFile = "cash-e2e-" + testId + ".csv";
        String backOfficeCode = "TEST";
        String frontTimestamp = "20261025_114000";

        Path inputFile = tempDir.resolve(sourceFile);
        Path frontOutputDirectory = tempDir.resolve("front-output");

        Files.writeString(
                inputFile,
                "accountCode,adjustmentId,currency,amount\n"
                        + "ACC001-" + testId + ",ADJ001,EUR,150.50\n"
                        + "ACC002-" + testId + ",ADJ002,USD,200.00\n"
                        + "ACC003-" + testId + ",ADJ003,XYZ,75.00\n"
                        + ",ADJ004,GBP,50.00\n",
                StandardCharsets.UTF_8
        );

        try {
            PipelineDefinition pipeline = new PipelineDefinition(
                    "cash-sqlserver-e2e-" + testId,
                    "Cash SQL Server End-to-End",
                    "Extract, validate, load and generate FRONT output",
                    List.of(
                            new PipelineStep(
                                    "extract-cash",
                                    "Extract cash CSV for validation",
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
                                    "extract-raw-cash",
                                    "Preserve original CSV rows",
                                    StepType.EXTRACT,
                                    2,
                                    new StepConfiguration(Map.of(
                                            "path", inputFile.toString(),
                                            "outputKey", "rawCashRecords",
                                            "requiredHeaders",
                                            "accountCode,adjustmentId,currency,amount"
                                    ))
                            ),
                            new PipelineStep(
                                    "validate-cash",
                                    "Validate cash records",
                                    StepType.TRANSFORM,
                                    3,
                                    new StepConfiguration(Map.of(
                                            "connector", "cash-validator",
                                            "inputKey", "cashRecords",
                                            "validOutputKey", "validCash",
                                            "rejectedOutputKey", "rejectedCash"
                                    ))
                            ),
                            new PipelineStep(
                                    "prepare-front-cash",
                                    "Prepare FRONT CSV payload",
                                    StepType.LOAD,
                                    4,
                                    new StepConfiguration(Map.of(
                                            "connector", "cash-front-csv-prepare",
                                            "inputKey", "validCash",
                                            "outputDirectory",
                                            frontOutputDirectory.toString(),
                                            "backOfficeCode", backOfficeCode,
                                            "columns",
                                            "accountCode,currency,amount",
                                            "delimiter", ",",
                                            "timestamp", frontTimestamp
                                    ))
                            ),
                            new PipelineStep(
                                    "load-cash",
                                    "Load cash and pending delivery into SQL Server",
                                    StepType.LOAD,
                                    5,
                                    new StepConfiguration(Map.of(
                                            "connector", "sqlserver",
                                            "inputKey", "validCash",
                                            "rejectedInputKey", "rejectedCash",
                                            "rawInputKey", "rawCashRecords",
                                            "backOfficeCode", backOfficeCode,
                                            "sourceFile", sourceFile
                                    ))
                            ),
                            new PipelineStep(
                                    "publish-front-cash",
                                    "Publish FRONT CSV",
                                    StepType.LOAD,
                                    6,
                                    new StepConfiguration(Map.of(
                                            "connector", "cash-output-publisher",
                                            "sourceFile", sourceFile
                                    ))
                            )
                    )
            );

            List<StepExecutor> executors = List.of(
                    new CsvExtractor(),
                    new CashValidator(),
                    new CashFrontCsvPrepareLoader(),
                    new SqlServerCashStagingLoader(),
                    new CashOutputPublisherLoader(),
                    new CashFrontCsvLoader()
            );

            PipelineExecutor executor = new PipelineExecutor(executors);
            PipelineExecutionResult result = executor.execute(pipeline);

            assertEquals(PipelineStatus.SUCCEEDED, result.status());

            // Verify the durable delivery record and its publication status.
            String deliverySql = """
                    SELECT BackOfficeCode, OutputFileName, OutputDirectory,
                           PayloadSha256, DeliveryStatus, PublishedAt
                    FROM dbo.CashOutputDelivery
                    WHERE SourceFile = ?
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(deliverySql)) {

                statement.setString(1, sourceFile);

                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(backOfficeCode,
                            rows.getString("BackOfficeCode"));
                    assertEquals(
                            "newcash_" + backOfficeCode + "_"
                                    + frontTimestamp + ".csv",
                            rows.getString("OutputFileName"));
                    assertEquals(
                            frontOutputDirectory.toAbsolutePath().normalize().toString(),
                            Path.of(rows.getString("OutputDirectory"))
                                    .toAbsolutePath().normalize().toString());
                    assertTrue(rows.getString("PayloadSha256")
                            .matches("[0-9a-f]{64}"));
                    assertEquals("PUBLISHED", rows.getString("DeliveryStatus"));
                    assertNotNull(rows.getTimestamp("PublishedAt"));
                    assertTrue(!rows.next(),
                            "Exactly one delivery record should exist");
                }
            }

            // Verify that the FRONT CSV contains only accepted records.
            Path frontOutput = frontOutputDirectory.resolve(
                    "newcash_" + backOfficeCode + "_"
                            + frontTimestamp + ".csv"
            );

            assertTrue(
                    Files.isRegularFile(frontOutput),
                    "The FRONT output CSV must be created"
            );

            List<String> frontLines = Files.readAllLines(
                    frontOutput,
                    StandardCharsets.UTF_8
            );

            assertEquals(
                    "accountCode,currency,amount",
                    frontLines.get(0)
            );
            assertEquals(
                    3,
                    frontLines.size(),
                    "FRONT CSV must contain a header and two accepted records"
            );
            assertTrue(frontLines.get(1).startsWith(
                    "ACC001-" + testId + ",EUR,"
            ));
            assertTrue(frontLines.get(2).startsWith(
                    "ACC002-" + testId + ",USD,"
            ));

            String frontContent = String.join("\n", frontLines);
            assertTrue(!frontContent.contains("ADJ003"));
            assertTrue(!frontContent.contains("ADJ004"));

            // Verify the two accepted records in CashStaging.
            String stagingSql = """
                    SELECT AccountCode, AdjustmentId, Currency, Amount
                    FROM dbo.CashStaging
                    WHERE SourceFile = ?
                    ORDER BY AdjustmentId
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(stagingSql)) {

                statement.setString(1, sourceFile);

                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(
                            "ACC001-" + testId,
                            rows.getString("AccountCode")
                    );
                    assertEquals("ADJ001", rows.getString("AdjustmentId"));
                    assertEquals("EUR", rows.getString("Currency"));
                    assertEquals(
                            0,
                            new BigDecimal("150.50").compareTo(
                                    rows.getBigDecimal("Amount")
                            )
                    );

                    assertTrue(rows.next());
                    assertEquals(
                            "ACC002-" + testId,
                            rows.getString("AccountCode")
                    );
                    assertEquals("ADJ002", rows.getString("AdjustmentId"));
                    assertEquals("USD", rows.getString("Currency"));
                    assertEquals(
                            0,
                            new BigDecimal("200.00").compareTo(
                                    rows.getBigDecimal("Amount")
                            )
                    );

                    assertTrue(
                            !rows.next(),
                            "Exactly two valid records should be staged"
                    );
                }
            }

            // Verify the two rejected records.
            String rejectedSql = """
                    SELECT AdjustmentId, Currency, Amount, RejectionReason
                    FROM dbo.CashRejected
                    WHERE SourceFile = ?
                    ORDER BY AdjustmentId
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(rejectedSql)) {

                statement.setString(1, sourceFile);

                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("ADJ003", rows.getString("AdjustmentId"));
                    assertEquals("XYZ", rows.getString("Currency"));
                    assertEquals("75.00", rows.getString("Amount"));
                    assertEquals(
                            "Unsupported currency: XYZ",
                            rows.getString("RejectionReason")
                    );

                    assertTrue(rows.next());
                    assertEquals("ADJ004", rows.getString("AdjustmentId"));
                    assertEquals("GBP", rows.getString("Currency"));
                    assertEquals("50.00", rows.getString("Amount"));
                    assertEquals(
                            "Missing accountCode",
                            rows.getString("RejectionReason")
                    );

                    assertTrue(
                            !rows.next(),
                            "Exactly two rejected records should be stored"
                    );
                }
            }

            // Verify all four source rows in T_MASTER_CASH.
            String masterSql = """
                    SELECT SourceRowNumber, BackOfficeCode, AccountCode,
                           AdjustmentId, ProcessingStatus, RejectionReason,
                           RawRecord
                    FROM dbo.T_MASTER_CASH
                    WHERE SourceFile = ?
                    ORDER BY SourceRowNumber
                    """;

            try (Connection connection =
                         SqlServerConnectionFactory.openConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(masterSql)) {

                statement.setString(1, sourceFile);

                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(1L, rows.getLong("SourceRowNumber"));
                    assertEquals(
                            backOfficeCode,
                            rows.getString("BackOfficeCode")
                    );
                    assertEquals(
                            "ACC001-" + testId,
                            rows.getString("AccountCode")
                    );
                    assertEquals("ADJ001", rows.getString("AdjustmentId"));
                    assertEquals(
                            "ACCEPTED",
                            rows.getString("ProcessingStatus")
                    );
                    assertEquals(null, rows.getString("RejectionReason"));

                    String rawFirst = rows.getString("RawRecord");
                    assertNotNull(rawFirst);
                    assertTrue(rawFirst.contains("\"amount\":\"150.50\""));
                    assertTrue(!rawFirst.contains("_sourceRowNumber"));
                    assertTrue(!rawFirst.contains("rejectionReason"));

                    assertTrue(rows.next());
                    assertEquals(2L, rows.getLong("SourceRowNumber"));
                    assertEquals("ADJ002", rows.getString("AdjustmentId"));
                    assertEquals(
                            "ACCEPTED",
                            rows.getString("ProcessingStatus")
                    );

                    assertTrue(rows.next());
                    assertEquals(3L, rows.getLong("SourceRowNumber"));
                    assertEquals("ADJ003", rows.getString("AdjustmentId"));
                    assertEquals(
                            "REJECTED",
                            rows.getString("ProcessingStatus")
                    );
                    assertEquals(
                            "Unsupported currency: XYZ",
                            rows.getString("RejectionReason")
                    );

                    String rawThird = rows.getString("RawRecord");
                    assertTrue(rawThird.contains("\"currency\":\"XYZ\""));
                    assertTrue(!rawThird.contains("rejectionReason"));
                    assertTrue(!rawThird.contains("_sourceRowNumber"));

                    assertTrue(rows.next());
                    assertEquals(4L, rows.getLong("SourceRowNumber"));
                    assertEquals("ADJ004", rows.getString("AdjustmentId"));
                    assertEquals(
                            "REJECTED",
                            rows.getString("ProcessingStatus")
                    );
                    assertEquals(
                            "Missing accountCode",
                            rows.getString("RejectionReason")
                    );

                    String rawFourth = rows.getString("RawRecord");
                    assertTrue(rawFourth.contains("\"accountCode\":\"\""));
                    assertTrue(!rawFourth.contains("rejectionReason"));
                    assertTrue(!rawFourth.contains("_sourceRowNumber"));

                    assertTrue(
                            !rows.next(),
                            "The master table must contain all four source rows"
                    );
                }
            }

            // Reprocessing the same file must not create duplicate rows.
            PipelineExecutionResult duplicateResult =
                    executor.execute(pipeline);

            assertEquals(PipelineStatus.FAILED, duplicateResult.status());
            assertNotNull(duplicateResult.errorMessage());
            assertTrue(
                    duplicateResult.errorMessage().contains("already been loaded")
            );

            assertEquals(2L, countForSource("dbo.CashStaging", sourceFile));
            assertEquals(2L, countForSource("dbo.CashRejected", sourceFile));
            assertEquals(4L, countForSource("dbo.T_MASTER_CASH", sourceFile));

        } finally {
            cleanupSource(sourceFile);
        }
    }

    private long countForSource(String table, String sourceFile)
            throws Exception {

        if (!List.of(
                "dbo.CashStaging",
                "dbo.CashRejected",
                "dbo.T_MASTER_CASH"
        ).contains(table)) {
            throw new IllegalArgumentException("Unexpected table name");
        }

        String sql = "SELECT COUNT_BIG(*) FROM " + table
                + " WHERE SourceFile = ?";

        try (Connection connection =
                     SqlServerConnectionFactory.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, sourceFile);

            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    private void cleanupSource(String sourceFile) throws Exception {
        try (Connection connection =
                     SqlServerConnectionFactory.openConnection()) {

            connection.setAutoCommit(false);

            try {
                deleteBySource(connection, "dbo.CashOutputDelivery", sourceFile);
                deleteBySource(connection, "dbo.T_MASTER_CASH", sourceFile);
                deleteBySource(connection, "dbo.CashRejected", sourceFile);
                deleteBySource(connection, "dbo.CashStaging", sourceFile);
                deleteBySource(connection, "dbo.CashLoadControl", sourceFile);

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void deleteBySource(
            Connection connection,
            String table,
            String sourceFile
    ) throws Exception {

        if (!List.of(
                "dbo.CashOutputDelivery",
                "dbo.T_MASTER_CASH",
                "dbo.CashRejected",
                "dbo.CashStaging",
                "dbo.CashLoadControl"
        ).contains(table)) {
            throw new IllegalArgumentException("Unexpected table name");
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE SourceFile = ?"
        )) {
            statement.setString(1, sourceFile);
            statement.executeUpdate();
        }
    }
}