
package com.flowforge.etl.connectors.file.csv;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import java.nio.charset.StandardCharsets;
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
    private static final String DELIMITER_PROPERTY = "delimiter";
    private static final String REQUIRED_HEADERS_PROPERTY = "requiredHeaders";

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
     * @param step    the pipeline step to execute
     * @param context the shared pipeline execution context
     */
    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String filePath = getRequiredProperty(step, PATH_PROPERTY);
        String outputKey = getRequiredProperty(step, OUTPUT_KEY_PROPERTY);

        String delimiterValue = step.configuration().get(DELIMITER_PROPERTY);
        char delimiter = resolveDelimiter(delimiterValue);

        String requiredHeadersValue =
                step.configuration().get(REQUIRED_HEADERS_PROPERTY);
        List<String> requiredHeaders =
                parseRequiredHeaders(requiredHeadersValue);

        List<Map<String, String>> records =
                readCsv(filePath, delimiter, requiredHeaders);

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
     * Resolves the configured CSV delimiter.
     *
     * @param value the configured delimiter
     * @return the delimiter character, defaulting to comma
     */
    private char resolveDelimiter(String value) {
        if (value == null || value.isBlank()) {
            return ',';
        }

        if (value.length() != 1) {
            throw new IllegalArgumentException(
                    "Delimiter must be a single character"
            );
        }

        return value.charAt(0);
    }

    /**
     * Parses the configured list of required CSV headers.
     *
     * @param value comma-separated header names
     * @return a list of trimmed, non-empty header names
     */
    private List<String> parseRequiredHeaders(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(header -> !header.isEmpty())
                .toList();
    }

    /**
     * Validates that all required headers are present in the CSV file.
     *
     * @param headers         headers found in the CSV file
     * @param requiredHeaders headers required by the pipeline configuration
     * @throws IllegalArgumentException if required headers are missing
     */
    private void validateRequiredHeaders(
            List<String> headers,
            List<String> requiredHeaders
    ) {
        List<String> missingHeaders = requiredHeaders.stream()
                .filter(header -> !headers.contains(header))
                .toList();

        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required CSV headers: " + missingHeaders
            );
        }
    }

    /**
     * Reads CSV records and validates the required headers.
     *
     * @param filePath        path to the CSV file
     * @param delimiter       CSV field delimiter
     * @param requiredHeaders headers that must be present
     * @return a list of CSV records represented as maps
     */
    private List<Map<String, String>> readCsv(
            String filePath,
            char delimiter,
            List<String> requiredHeaders
    ) {
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (
                BufferedReader reader = Files.newBufferedReader(
                        Path.of(filePath),
                        StandardCharsets.UTF_8
                )
        ) {
            // Elimina el BOM UTF-8 si existe al principio del archivo.
            reader.mark(1);

            if (reader.read() != '\uFEFF') {
                reader.reset();
            }

            try (CSVParser parser = format.parse(reader)) {
                List<Map<String, String>> records = new ArrayList<>();

                List<String> headers = parser.getHeaderNames();
                validateRequiredHeaders(headers, requiredHeaders);

                for (CSVRecord record : parser) {
                    if (record.size() != headers.size()) {
                        throw new IllegalArgumentException(
                                "Invalid number of fields in CSV record at line "
                                        + record.getRecordNumber()
                                        + ": expected " + headers.size()
                                        + " but found " + record.size()
                        );
                    }

                    Map<String, String> row = new LinkedHashMap<>();

                    for (String header : headers) {
                        row.put(header, record.get(header));
                    }

                    records.add(Map.copyOf(row));
                }

                return records;
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to read CSV file: " + filePath,
                    exception
            );
        }
    }
}