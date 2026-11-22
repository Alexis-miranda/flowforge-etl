package com.flowforge.etl.cash.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the standard Cash ETL pipeline definition.
 */
public final class CashPipelineDefinitionFactory {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private CashPipelineDefinitionFactory() {
    }

    /**
     * Builds a Cash ETL pipeline using the supplied input and delivery settings.
     */
    public static PipelineDefinition create(
            Path input,
            String backOfficeCode,
            Path outputDirectory,
            String columns,
            String sourceFile) {

        return create(
                "cash-" + UUID.randomUUID(),
                input, backOfficeCode, outputDirectory, columns, sourceFile);
    }

    /**
     * Builds a Cash ETL pipeline with a caller-supplied stable pipeline ID.
     * Use a stable ID when a pipeline definition is reconstructed from a
     * redelivered asynchronous request.
     */
    public static PipelineDefinition create(
            String pipelineId,
            Path input,
            String backOfficeCode,
            Path outputDirectory,
            String columns,
            String sourceFile) {
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new IllegalArgumentException("Pipeline ID must not be blank");
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP);

        return new PipelineDefinition(
                pipelineId,
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
                                        "outputDirectory", outputDirectory.toString(),
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
    }

    private static StepConfiguration config(
            Map<String, String> properties) {
        return new StepConfiguration(properties);
    }
}
