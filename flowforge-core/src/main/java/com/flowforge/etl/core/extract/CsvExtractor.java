
package com.flowforge.etl.core.extract;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts records from CSV files.
 */
public class CsvExtractor implements StepExecutor {

    private static final String PATH_PROPERTY = "path";
    private static final String OUTPUT_KEY_PROPERTY = "outputKey";

    /**
     * Returns the type of step supported by this executor.
     *
     * @return the EXTRACT step type
     */
    @Override
    public StepType supportedType() {
        return StepType.EXTRACT;
    }

    /**
     * Reads a CSV file and stores its records in the execution context.
     *
     * @param step the pipeline step to execute
     * @param context the shared pipeline execution context
     */
    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String filePath = getRequiredProperty(step, PATH_PROPERTY);
        String outputKey = getRequiredProperty(step, OUTPUT_KEY_PROPERTY);

        List<Map<String, String>> records = readCsv(filePath);
        context.put(outputKey, List.copyOf(records));
    }

    /**
     * Validates and retrieves a required configuration property.
     *
     * @param step the pipeline step
     * @param name the property name
     * @return the configured property value
     */
    private String getRequiredProperty(PipelineStep step, String name) {
        String value = step.configuration().get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank configuration property: " + name
            );
        }

        return value;
    }

    /**
     * Reads CSV records using the first row as the header.
     *
     * @param filePath the path to the CSV file
     * @return the records as a list of column-name/value maps
     */
    private List<Map<String, String>> readCsv(String filePath) {
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (
                BufferedReader reader =
                        Files.newBufferedReader(Path.of(filePath));
                CSVParser parser = format.parse(reader)
        ) {
            List<Map<String, String>> records = new ArrayList<>();
            List<String> headers = parser.getHeaderNames();

            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();

                for (String header : headers) {
                    row.put(header, record.get(header));
                }

                records.add(Map.copyOf(row));
            }

            return records;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to read CSV file: " + filePath,
                    exception
            );
        }
    }
}

