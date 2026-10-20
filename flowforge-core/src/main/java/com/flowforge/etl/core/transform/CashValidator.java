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

/**
 * Validates cash records and separates accepted and rejected records.
 */
public class CashValidator implements StepExecutor {

    private static final Set<String> DEFAULT_CURRENCIES =
            Set.of("EUR", "USD", "GBP", "CHF");

    @Override
    public StepType supportedType() {
        return StepType.TRANSFORM;
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String inputKey = requiredProperty(step, "inputKey");
        String validOutputKey = requiredProperty(step, "validOutputKey");
        String rejectedOutputKey = requiredProperty(step, "rejectedOutputKey");

        if (validOutputKey.equals(rejectedOutputKey)) {
            throw new IllegalArgumentException(
                    "Valid and rejected output keys must be different"
            );
        }

        if (!context.contains(inputKey)) {
            throw new IllegalArgumentException(
                    "Missing input data in execution context: " + inputKey
            );
        }

        Object input = context.get(inputKey);

        if (!(input instanceof List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey
            );
        }

        Set<String> currencies = configuredCurrencies(step);

        List<Map<String, String>> validRecords = new ArrayList<>();
        List<Map<String, String>> rejectedRecords = new ArrayList<>();

        for (Object record : records) {
            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map"
                );
            }

            Map<String, String> cashRecord = toStringMap(row);
            String rejectionReason = validate(cashRecord, currencies);

            if (rejectionReason == null) {
                validRecords.add(Map.copyOf(cashRecord));
            } else {
                Map<String, String> rejected = new LinkedHashMap<>(cashRecord);
                rejected.put("rejectionReason", rejectionReason);
                rejectedRecords.add(Map.copyOf(rejected));
            }
        }

        context.put(validOutputKey, List.copyOf(validRecords));
        context.put(rejectedOutputKey, List.copyOf(rejectedRecords));
    }

    private String validate(
            Map<String, String> record,
            Set<String> currencies
    ) {
        String account = record.get("accountCode");
        String currency = record.get("currency");
        String amount = record.get("amount");

        if (account == null || account.isBlank()) {
            return "Missing accountCode";
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
                    "At least one allowed currency must be configured"
            );
        }

        return Set.copyOf(currencies);
    }

    private String requiredProperty(PipelineStep step, String name) {
        String value = step.configuration().get(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or blank configuration property: " + name
            );
        }

        return value;
    }
}