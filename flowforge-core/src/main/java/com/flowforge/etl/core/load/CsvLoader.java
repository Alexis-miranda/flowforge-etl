
package com.flowforge.etl.core.load;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes records to CSV files.
 */
public class CsvLoader implements StepExecutor {

    private static final String PATH_PROPERTY = "path";
    private static final String INPUT_KEY_PROPERTY = "inputKey";

    /**
     * Returns the type of step supported by this executor.
     *
     * @return the LOAD step type
     */
    @Override
    public StepType supportedType() {
        return StepType.LOAD;
    }

    /**
     * Writes records from the execution context to a CSV file.
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
        String inputKey = getRequiredProperty(step, INPUT_KEY_PROPERTY);

        Object input = context.get(inputKey);

        if (!(input instanceof List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey
            );
        }

        writeCsv(filePath, records);
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
     * Writes records using their map keys as CSV headers.
     *
     * @param filePath the output file path
     * @param records the records to write
     */
    private void writeCsv(String filePath, List<?> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot write CSV without records or headers"
            );
        }

        if (!(records.get(0) instanceof Map<?, ?> firstRecord)) {
            throw new IllegalArgumentException(
                    "Each input record must be a map"
            );
        }

        List<String> headers = firstRecord.keySet().stream()
                .map(key -> {
                    if (!(key instanceof String)) {
                        throw new IllegalArgumentException(
                                "Record keys must be strings"
                        );
                    }
                    return (String) key;
                })
                .toList();

        try {
            Path outputPath = Path.of(filePath);
            Path parent = outputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (
                    BufferedWriter writer =
                            Files.newBufferedWriter(outputPath);
                    CSVPrinter printer = new CSVPrinter(
                            writer,
                            CSVFormat.RFC4180.builder()
                                    .setHeader(headers.toArray(String[]::new))
                                    .get()
                    )
            ) {
                for (Object record : records) {
                    if (!(record instanceof Map<?, ?> row)) {
                        throw new IllegalArgumentException(
                                "Each input record must be a map"
                        );
                    }

                    for (Object key : row.keySet()) {
                        if (!(key instanceof String stringKey)
                                || !headers.contains(stringKey)) {
                            throw new IllegalArgumentException(
                                    "All records must have the same columns"
                            );
                        }
                    }

                    for (String header : headers) {
                        if (!row.containsKey(header)) {
                            throw new IllegalArgumentException(
                                    "All records must have the same columns"
                            );
                        }

                        Object value = row.get(header);

                        if (!(value instanceof String stringValue)) {
                            throw new IllegalArgumentException(
                                    "Record values must be strings"
                            );
                        }

                        printer.print(stringValue);
                    }

                    printer.println();
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to write CSV file: " + filePath,
                    exception
            );
        }
    }
}
