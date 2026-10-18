
package com.flowforge.etl.core.integration;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;
import com.flowforge.etl.core.extract.CsvExtractor;
import com.flowforge.etl.core.load.CsvLoader;
import com.flowforge.etl.core.transform.UppercaseTransformer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.flowforge.etl.core.domain.PipelineStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteCompleteCsvPipeline() throws IOException {
        Path inputFile = tempDir.resolve("customers.csv");
        Path outputFile = tempDir.resolve("output/customers.csv");

        Files.writeString(
                inputFile,
                "name,city\nAna,Geneva\nCarlos,Madrid\n",
                StandardCharsets.UTF_8
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "customer-pipeline",
                "Customer CSV Pipeline",
                "Extract, transform and load customer data",
                List.of(
                        new PipelineStep(
                                "extract-customers",
                                "Extract customers",
                                StepType.EXTRACT,
                                1,
                                new StepConfiguration(Map.of(
                                        "path", inputFile.toString(),
                                        "outputKey", "customers"
                                ))
                        ),
                        new PipelineStep(
                                "uppercase-names",
                                "Uppercase customer names",
                                StepType.TRANSFORM,
                                2,
                                new StepConfiguration(Map.of(
                                        "inputKey", "customers",
                                        "outputKey", "transformedCustomers",
                                        "column", "name"
                                ))
                        ),
                        new PipelineStep(
                                "load-customers",
                                "Load customers",
                                StepType.LOAD,
                                3,
                                new StepConfiguration(Map.of(
                                        "path", outputFile.toString(),
                                        "inputKey", "transformedCustomers"
                                ))
                        )
                )
        );

        List<StepExecutor> executors = List.of(
                new CsvExtractor(),
                new UppercaseTransformer(),
                new CsvLoader()
        );

        PipelineExecutor pipelineExecutor =
                new PipelineExecutor(executors);

        PipelineExecutionResult result =
                pipelineExecutor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());

        assertTrue(Files.exists(outputFile));

        try (CSVParser parser = CSVParser.parse(
                outputFile,
                StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            List<CSVRecord> records = parser.getRecords();

            assertEquals(2, records.size());
            assertEquals("ANA", records.get(0).get("name"));
            assertEquals("Geneva", records.get(0).get("city"));
            assertEquals("CARLOS", records.get(1).get("name"));
            assertEquals("Madrid", records.get(1).get("city"));
        }
    }
}
