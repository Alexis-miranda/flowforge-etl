package com.flowforge.etl.connectors.integration;


import com.flowforge.etl.connectors.file.csv.CsvExtractor;
import com.flowforge.etl.connectors.file.csv.CsvLoader;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;

import com.flowforge.etl.core.transform.CashValidator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class CashPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldValidateCashRecordsAndGenerateOutputFiles() throws IOException {
        Path inputFile = tempDir.resolve("cash-input.csv");
        Path validFile = tempDir.resolve("output/cash-valid.csv");
        Path rejectedFile = tempDir.resolve("output/cash-rejected.csv");

        Files.writeString(
                inputFile,
                "accountCode,adjustmentId,currency,amount\n"
                        + "ACC001,ADJ001,EUR,150.50\n"
                        + "ACC002,ADJ002,USD,200.00\n"
                        + "ACC003,ADJ003,XYZ,75.00\n"
                        + ",ADJ004,GBP,50.00\n",
                StandardCharsets.UTF_8
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "cash-pipeline",
                "Cash Validation Pipeline",
                "Extract, validate and export cash records",
                List.of(
                        new PipelineStep(
                                "extract-cash",
                                "Extract cash records",
                                StepType.EXTRACT,
                                1,
                                new StepConfiguration(Map.of(
                                        "path", inputFile.toString(),
                                        "outputKey", "cashRecords"
                                ))
                        ),
                        new PipelineStep(
                                "validate-cash",
                                "Validate cash records",
                                StepType.TRANSFORM,
                                2,
                                new StepConfiguration(Map.of(
                                        "inputKey", "cashRecords",
                                        "validOutputKey", "validCash",
                                        "rejectedOutputKey", "rejectedCash"
                                ))
                        ),
                        new PipelineStep(
                                "load-valid-cash",
                                "Export valid cash records",
                                StepType.LOAD,
                                3,
                                new StepConfiguration(Map.of(
                                        "path", validFile.toString(),
                                        "inputKey", "validCash"
                                ))
                        ),
                        new PipelineStep(
                                "load-rejected-cash",
                                "Export rejected cash records",
                                StepType.LOAD,
                                4,
                                new StepConfiguration(Map.of(
                                        "path", rejectedFile.toString(),
                                        "inputKey", "rejectedCash"
                                ))
                        )
                )
        );

        List<StepExecutor> executors = List.of(
                new CsvExtractor(),
                new CashValidator(),
                new CsvLoader()
        );

        PipelineExecutor pipelineExecutor = new PipelineExecutor(executors);

        PipelineExecutionResult result = pipelineExecutor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertTrue(Files.exists(validFile));
        assertTrue(Files.exists(rejectedFile));

        try (CSVParser parser = CSVParser.parse(
                validFile,
                StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            List<CSVRecord> records = parser.getRecords();

            assertEquals(2, records.size());
            assertEquals("ACC001", records.get(0).get("accountCode"));
            assertEquals("ACC002", records.get(1).get("accountCode"));
        }

        try (CSVParser parser = CSVParser.parse(
                rejectedFile,
                StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            List<CSVRecord> records = parser.getRecords();

            assertEquals(2, records.size());
            assertEquals("ACC003", records.get(0).get("accountCode"));
            assertEquals(
                    "Unsupported currency: XYZ",
                    records.get(0).get("rejectionReason")
            );
            assertEquals("", records.get(1).get("accountCode"));
            assertEquals(
                    "Missing accountCode",
                    records.get(1).get("rejectionReason")
            );
        }
    }
}
