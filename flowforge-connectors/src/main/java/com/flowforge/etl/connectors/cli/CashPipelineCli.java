package com.flowforge.etl.connectors.cli;

import com.flowforge.etl.connectors.integration.PipelineExecutorFactory;
import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CashPipelineCli {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private CashPipelineCli() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception exception) {
            System.err.println("Cash pipeline failed: "
                    + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) {
        if (args.length < 3 || args.length > 5
                || (args.length > 0
                && ("--help".equals(args[0]) || "-h".equals(args[0])))) {
            printUsage();

            if (args.length > 0
                    && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
                return;
            }

            throw new IllegalArgumentException(
                    "Expected 3 to 5 arguments.");
        }

        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        String backOfficeCode = args[1];
        Path outputDirectory =
                Path.of(args[2]).toAbsolutePath().normalize();

        String columns = args.length >= 4
                ? args[3]
                : "accountCode,currency,amount";

        String sourceFile = args.length >= 5
                ? args[4]
                : input.getFileName().toString();

        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new IllegalArgumentException(
                    "Input CSV does not exist or is not readable: " + input);
        }

        if (sourceFile.isBlank() || sourceFile.length() > 260) {
            throw new IllegalArgumentException(
                    "sourceFile must contain 1 to 260 characters.");
        }

        if (backOfficeCode == null
                || !backOfficeCode.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "backOfficeCode may contain only letters, digits, '_' and '-'.");
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP);
        String runId = UUID.randomUUID().toString();

        PipelineDefinition pipeline = new PipelineDefinition(
                "cash-" + runId,
                "Cash ETL",
                "Extract, validate, prepare, persist and publish FRONT Cash CSV",
                List.of(
                        new PipelineStep(
                                "extract-cash",
                                "Extract input CSV",
                                StepType.EXTRACT,
                                1,
                                config(Map.of(
                                        "connector", "default",
                                        "path", input.toString(),
                                        "outputKey", "rawCashRecords",
                                        "delimiter", ",",
                                        "requiredHeaders",
                                        "accountCode,adjustmentId,currency,amount"
                                ))
                        ),
                        new PipelineStep(
                                "validate-cash",
                                "Validate Cash records",
                                StepType.TRANSFORM,
                                2,
                                config(Map.of(
                                        "connector", "cash-validator",
                                        "inputKey", "rawCashRecords",
                                        "validOutputKey", "validCash",
                                        "rejectedOutputKey", "rejectedCash"
                                ))
                        ),
                        new PipelineStep(
                                "prepare-front-cash",
                                "Prepare FRONT CSV payload",
                                StepType.LOAD,
                                3,
                                config(Map.of(
                                        "connector", "cash-front-csv-prepare",
                                        "inputKey", "validCash",
                                        "outputDirectory",
                                        outputDirectory.toString(),
                                        "backOfficeCode", backOfficeCode,
                                        "columns", columns,
                                        "delimiter", ",",
                                        "timestamp", timestamp
                                ))
                        ),
                        new PipelineStep(
                                "persist-cash",
                                "Persist cash records and pending delivery",
                                StepType.LOAD,
                                4,
                                config(Map.of(
                                        "connector", "sqlserver",
                                        "inputKey", "validCash",
                                        "rejectedInputKey", "rejectedCash",
                                        "rawInputKey", "rawCashRecords",
                                        "sourceFile", sourceFile,
                                        "backOfficeCode", backOfficeCode
                                ))
                        ),
                        new PipelineStep(
                                "publish-front-cash",
                                "Publish FRONT CSV",
                                StepType.LOAD,
                                5,
                                config(Map.of(
                                        "connector", "cash-output-publisher",
                                        "sourceFile", sourceFile
                                ))
                        )
                )
        );

        System.out.println("Starting Cash ETL");
        System.out.println("Input: " + input);
        System.out.println("Source identifier: " + sourceFile);
        System.out.println("BackOffice: " + backOfficeCode);
        System.out.println("Output directory: " + outputDirectory);

        PipelineExecutor executor = PipelineExecutorFactory.create();
        PipelineExecutionResult result = executor.execute(pipeline);

        System.out.println("Pipeline status: " + result.status());
        System.out.println("Execution ID: " + result.executionId());
        System.out.println("Duration: " + result.duration().toMillis() + " ms");

        if (!result.succeeded()) {
            throw new IllegalStateException(
                    "Step '" + result.failedStepId()
                            + "' failed: " + result.errorMessage());
        }

        System.out.println("Cash ETL completed successfully.");
    }

    private static StepConfiguration config(
            Map<String, String> properties) {
        return new StepConfiguration(properties);
    }

    private static void printUsage() {
        System.out.println("""
                FlowForge Cash ETL

                Usage:
                  CashPipelineCli <input.csv> <backOfficeCode> <outputDir>
                                  [columns] [sourceFile]

                Example:
                  CashPipelineCli "C:\\data\\cash.csv" BNYM
                      "C:\\data\\out"
                      "accountCode,currency,amount"
                      "cash-delivery-20261025.csv"

                Required input columns:
                  accountCode, adjustmentId, currency, amount

                Default output columns:
                  accountCode,currency,amount
                """);
    }
}