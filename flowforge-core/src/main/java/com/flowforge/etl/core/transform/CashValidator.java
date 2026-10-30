package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CashValidator implements StepExecutor {

    private static final Set<String> DEFAULT_CURRENCIES =
            Set.of("EUR", "USD", "GBP", "CHF");

    private static final String SOURCE_ROW_NUMBER = "_sourceRowNumber";

    @Override
    public StepType supportedType() {
        return StepType.TRANSFORM;
    }

    @Override
    public String connectorKey() {
        return "cash-validator";
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context) {

        String inputKey = requiredProperty(step, "inputKey");
        String validOutputKey = requiredProperty(step, "validOutputKey");
        String rejectedOutputKey = requiredProperty(step, "rejectedOutputKey");

        if (validOutputKey.equals(rejectedOutputKey)) {
            throw new IllegalArgumentException(
                    "Valid and rejected output keys must be different");
        }

        if (inputKey.equals(validOutputKey)
                || inputKey.equals(rejectedOutputKey)) {
            throw new IllegalArgumentException(
                    "Input and output keys must be different");
        }

        if (!context.contains(inputKey)) {
            throw new IllegalArgumentException(
                    "Missing input data in execution context: " + inputKey);
        }

        Object input = context.get(inputKey);

        if (!(input instanceof List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey);
        }

        Set<String> currencies = configuredCurrencies(step);

        List<Map<String, String>> validRecords = new ArrayList<>();
        List<Map<String, String>> rejectedRecords = new ArrayList<>();

        for (int index = 0; index < records.size(); index++) {
            Object record = records.get(index);

            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map");
            }

            Map<String, String> cashRecord = toStringMap(row);

            if (cashRecord.containsKey(SOURCE_ROW_NUMBER)) {
                throw new IllegalArgumentException(
                        "Input record uses reserved field: "
                                + SOURCE_ROW_NUMBER);
            }

            cashRecord.put(SOURCE_ROW_NUMBER, Integer.toString(index + 1));

            String rejectionReason = validate(cashRecord, currencies);

            if (rejectionReason == null) {
                cashRecord.put(
                        "currency",
                        cashRecord.get("currency").trim().toUpperCase(Locale.ROOT)
                );
                validRecords.add(Map.copyOf(cashRecord));
            } else {
                Map<String, String> rejected =
                        new LinkedHashMap<>(cashRecord);
                rejected.put("rejectionReason", rejectionReason);
                rejectedRecords.add(Map.copyOf(rejected));
            }
        }

        context.put(validOutputKey, List.copyOf(validRecords));
        context.put(rejectedOutputKey, List.copyOf(rejectedRecords));
    }

    private String validate(
            Map<String, String> record,
            Set<String> currencies) {

        String account = record.get("accountCode");
        String adjustmentId = record.get("adjustmentId");
        String currency = record.get("currency");
        String amount = record.get("amount");

        if (account == null || account.isBlank()) {
            return "Missing accountCode";
        }

        if (adjustmentId == null || adjustmentId.isBlank()) {
            return "Missing adjustmentId";
        }

        if (currency == null || currency.isBlank()) {
            return "Missing currency";
        }

        if (!currencies.contains(currency.trim().toUpperCase(Locale.ROOT))) {
            return "Unsupported currency: " + currency;
        }

        if (amount == null || amount.isBlank()) {
            return "Missing amount";
        }

        try {
            new BigDecimal(amount.trim());
        } catch (NumberFormatException exception) {
            return "Invalid amount: " + amount;
        }

        return null;
    }

    private Map<String, String> toStringMap(Map<?, ?> row) {
        Map<String, String> result = new LinkedHashMap<>();

        RecordMapper.forEachStringEntry(row, result::put);

        return result;
    }

    private Set<String> configuredCurrencies(PipelineStep step) {
        String value = step.configuration().get("allowedCurrencies");

        if (value == null || value.isBlank()) {
            return DEFAULT_CURRENCIES;
        }

        Set<String> currencies = new java.util.HashSet<>();

        for (String currency : value.split(",")) {
            String normalized = currency.trim().toUpperCase(Locale.ROOT);

            if (!normalized.isEmpty()) {
                currencies.add(normalized);
            }
        }

        if (currencies.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one allowed currency must be configured");
        }

        return Set.copyOf(currencies);
    }

    private String requiredProperty(PipelineStep step, String name) {
        String value = step.configuration().get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank configuration property: " + name);
        }

        return value;
    }
}