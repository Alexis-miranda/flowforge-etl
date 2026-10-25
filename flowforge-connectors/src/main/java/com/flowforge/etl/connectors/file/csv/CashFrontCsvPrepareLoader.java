package com.flowforge.etl.connectors.file.csv;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

public class CashFrontCsvPrepareLoader implements StepExecutor {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Pattern BACK_OFFICE_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]+");

    @Override
    public StepType supportedType() {
        return StepType.LOAD;
    }

    @Override
    public String connectorKey() {
        return "cash-front-csv-prepare";
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context) {

        String inputKey = required(step, "inputKey");
        String outputDirectory = required(step, "outputDirectory");
        String backOfficeCode = required(step, "backOfficeCode");
        String columns = required(step, "columns");

        if (!BACK_OFFICE_PATTERN.matcher(backOfficeCode).matches()) {
            throw new IllegalArgumentException(
                    "backOfficeCode may contain only letters, digits, "
                            + "underscores and hyphens");
        }

        Object input = context.get(inputKey);
        if (!(input instanceof List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey);
        }

        char delimiter = resolveDelimiter(
                step.configuration().get("delimiter"));
        String timestamp = resolveTimestamp(
                step.configuration().get("timestamp"));

        CashFrontCsvSerializer.SerializedCsv serialized =
                CashFrontCsvSerializer.serialize(records, columns, delimiter);

        String fileName = "newcash_" + backOfficeCode
                + "_" + timestamp + ".csv";

        Path directory = Path.of(outputDirectory)
                .toAbsolutePath().normalize();
        Path outputFile = directory.resolve(fileName).normalize();

        if (!outputFile.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "Output file must be directly inside outputDirectory");
        }

        context.put("cashFrontPayload", serialized.bytes());
        context.put("cashFrontSha256", serialized.sha256());
        context.put("cashFrontOutputDirectory", directory.toString());
        context.put("cashFrontOutputFileName", fileName);
        context.put("cashFrontOutputPath", outputFile.toString());
        context.put("cashFrontBackOfficeCode", backOfficeCode);
    }

    private String required(PipelineStep step, String key) {
        String value = step.configuration().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank configuration property: " + key);
        }
        return value;
    }

    private char resolveDelimiter(String value) {
        if (value == null || value.isBlank()) {
            return ',';
        }
        if (value.length() != 1) {
            throw new IllegalArgumentException(
                    "Delimiter must be a single character");
        }
        return value.charAt(0);
    }

    private String resolveTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now().format(FILE_TIMESTAMP);
        }
        try {
            return LocalDateTime.parse(value, FILE_TIMESTAMP)
                    .format(FILE_TIMESTAMP);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Timestamp must use yyyyMMdd_HHmmss format",
                    exception);
        }
    }
}