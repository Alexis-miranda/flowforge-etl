
package com.flowforge.etl.connectors.file.csv;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvLoaderTest {

    @TempDir
    Path tempDir;

    private final CsvLoader loader = new CsvLoader();


    @Test
    void shouldWriteRecordsToCsvFile() throws IOException {
        Path outputFile = tempDir.resolve("customers.csv");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva"),
                Map.of("name", "Carlos", "city", "Madrid")
        ));

        loader.execute(createStep(outputFile, "customers"), context);

        try (CSVParser parser = CSVParser.parse(
                outputFile,
                java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            assertEquals(
                    List.of("city", "name"),
                    parser.getHeaderNames().stream().sorted().toList()
            );

            List<CSVRecord> records = parser.getRecords();

            assertEquals(2, records.size());

            assertEquals("Ana", records.get(0).get("name"));
            assertEquals("Geneva", records.get(0).get("city"));

            assertEquals("Carlos", records.get(1).get("name"));
            assertEquals("Madrid", records.get(1).get("city"));
        }
    }

    @Test
    void shouldCreateParentDirectories() throws IOException {
        Path outputFile = tempDir.resolve("nested/output/customers.csv");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(Map.of("name", "Ana")));

        loader.execute(createStep(outputFile, "customers"), context);

        assertTrue(Files.exists(outputFile));
    }
    @Test
    void shouldWriteRecordsUsingConfiguredDelimiter() throws IOException {
        Path outputFile = tempDir.resolve("customers-semicolon.csv");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva"),
                Map.of("name", "Carlos", "city", "Madrid")
        ));

        PipelineStep step = new PipelineStep(
                "load-customers",
                "Load customers",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of(
                        "path", outputFile.toString(),
                        "inputKey", "customers",
                        "delimiter", ";"
                ))
        );

        loader.execute(step, context);
       // System.out.println(Files.readString(outputFile));

        try (CSVParser parser = CSVParser.parse(
                outputFile,
                java.nio.charset.StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setDelimiter(';')
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            List<CSVRecord> records = parser.getRecords();

            assertEquals(
                    List.of("city", "name"),
                    parser.getHeaderNames().stream().sorted().toList()
            );
            assertEquals(2, records.size());

            assertEquals("Ana", records.get(0).get("name"));
            assertEquals("Geneva", records.get(0).get("city"));
            assertEquals("Carlos", records.get(1).get("name"));
            assertEquals("Madrid", records.get(1).get("city"));
        }
    }


    @Test
    void shouldRejectDelimiterWithMoreThanOneCharacter() {
        Path outputFile = tempDir.resolve("customers.csv");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva")
        ));

        PipelineStep step = new PipelineStep(
                "load-customers",
                "Load customers",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of(
                        "path", outputFile.toString(),
                        "inputKey", "customers",
                        "delimiter", ";;"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(step, context)
        );

        assertEquals(
                "Delimiter must be a single character",
                exception.getMessage()
        );
    }
    @Test
    void shouldEscapeValuesContainingCommas() throws IOException {
        Path outputFile = tempDir.resolve("customers.csv");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva, Switzerland")
        ));

        loader.execute(createStep(outputFile, "customers"), context);

        String content = Files.readString(outputFile);

        assertTrue(content.contains("\"Geneva, Switzerland\""));
    }

    @Test
    void shouldRejectMissingPathConfiguration() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of(Map.of("name", "Ana")));

        PipelineStep step = new PipelineStep(
                "load-customers",
                "Load customers",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of("inputKey", "customers"))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(step, context)
        );

        assertEquals(
                "Missing or blank configuration property: path",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingInputData() {
        Path outputFile = tempDir.resolve("customers.csv");
        PipelineExecutionContext context = new PipelineExecutionContext();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(createStep(outputFile, "customers"), context)
        );

        assertEquals(
                "Input data must be a list of records: customers",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyRecords() {
        Path outputFile = tempDir.resolve("customers.csv");
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("customers", List.of());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(createStep(outputFile, "customers"), context)
        );

        assertEquals(
                "Cannot write CSV without records or headers",
                exception.getMessage()
        );
    }

    private PipelineStep createStep(Path outputFile, String inputKey) {
        return new PipelineStep(
                "load-customers",
                "Load customers",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of(
                        "path", outputFile.toString(),
                        "inputKey", inputKey
                ))
        );
    }
}
