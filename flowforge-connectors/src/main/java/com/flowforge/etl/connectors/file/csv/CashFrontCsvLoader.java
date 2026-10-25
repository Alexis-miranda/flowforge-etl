package com.flowforge.etl.connectors.file.csv;

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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CashFrontCsvLoader implements StepExecutor {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public StepType supportedType() {
        return StepType.LOAD;
    }

    @Override
    public String connectorKey() {
        return "cash-front-csv";
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String directory = required(step, "outputDirectory");
        String inputKey = required(step, "inputKey");
        String backOfficeCode = required(step, "backOfficeCode");

        validateBackOfficeCode(backOfficeCode);

        List<String> columns = parseColumns(required(step, "columns"));
        char delimiter = resolveDelimiter(
                step.configuration().get("delimiter")
        );

        String timestamp = resolveTimestamp(
                step.configuration().get("timestamp")
        );

        Object input = context.get(inputKey);

        if (!(input instanceof List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey
            );
        }

        if (records.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot write FRONT CSV without records"
            );
        }

        List<Map<String, String>> validatedRecords =
                validateRecords(records, columns);

        String fileName = "newcash_" + backOfficeCode
                + "_" + timestamp + ".csv";

        Path outputDirectory = Path.of(directory);
        Path outputFile = outputDirectory.resolve(fileName);

        writeCsv(outputDirectory, outputFile, columns,
                validatedRecords, delimiter);

        context.put("cashFrontOutputPath", outputFile.toString());
    }

    private List<Map<String, String>> validateRecords(
            List<?> records,
            List<String> columns
    ) {
        List<Map<String, String>> result = new ArrayList<>();

        for (Object record : records) {
            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map"
                );
            }

            for (String column : columns) {
                if (!row.containsKey(column)) {
                    throw new IllegalArgumentException(
                            "Missing FRONT output column: " + column
                    );
                }

                if (!(row.get(column) instanceof String)) {
                    throw new IllegalArgumentException(
                            "FRONT output values must be strings: " + column
                    );
                }
            }

            result.add(toStringMap(row));
        }

        return List.copyOf(result);
    }

    private Map<String, String> toStringMap(Map<?, ?> row) {
        java.util.LinkedHashMap<String, String> result =
                new java.util.LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : row.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException(
                        "Record keys and values must be strings"
                );
            }

            result.put(key, value);
        }

        return result;
    }

    private void writeCsv(
            Path directory,
            Path outputFile,
            List<String> columns,
            List<Map<String, String>> records,
            char delimiter
    ) {
        try {
            Files.createDirectories(directory);

            // CREATE_NEW prevents accidental replacement of an existing file.
            try (
                    BufferedWriter writer = Files.newBufferedWriter(
                            outputFile,
                            java.nio.charset.StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE
                    );
                    CSVPrinter printer = new CSVPrinter(
                            writer,
                            CSVFormat.RFC4180.builder()
                                    .setDelimiter(delimiter)
                                    .setHeader(columns.toArray(String[]::new))
                                    .get()
                    )
            ) {
                for (Map<String, String> record : records) {
                    for (String column : columns) {
                        printer.print(record.get(column));
                    }
                    printer.println();
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to write FRONT CSV: " + outputFile,
                    exception
            );
        }
    }

    private List<String> parseColumns(String value) {
        List<String> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String item : value.split(",", -1)) {
            String column = item.trim();

            if (column.isEmpty()) {
                throw new IllegalArgumentException(
                        "FRONT columns must not contain blank names"
                );
            }

            if (!seen.add(column)) {
                throw new IllegalArgumentException(
                        "Duplicate FRONT output column: " + column
                );
            }

            columns.add(column);
        }

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one FRONT output column is required"
            );
        }

        return List.copyOf(columns);
    }

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

    private String resolveTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now().format(FILE_TIMESTAMP);
        }

        try {
            LocalDateTime parsed =
                    LocalDateTime.parse(value, FILE_TIMESTAMP);

            return parsed.format(FILE_TIMESTAMP);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Timestamp must use yyyyMMdd_HHmmss format",
                    exception
            );
        }
    }

    private void validateBackOfficeCode(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "backOfficeCode may contain only letters, digits, "
                            + "underscores and hyphens"
            );
        }
    }

    private String required(PipelineStep step, String name) {
        String value = step.configuration().get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank configuration property: " + name
            );
        }

        return value;
    }
}