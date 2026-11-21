package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ColumnMappingTransformer implements StepExecutor {

    @Override
    public StepType supportedType() {
        return StepType.TRANSFORM;
    }

    @Override
    public String connectorKey() {
        return "column-mapping";
    }

    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String inputKey = required(step, "inputKey");
        String outputKey = required(step, "outputKey");
        String mappingsValue = required(step, "mappings");

        if (inputKey.equals(outputKey)) {
            throw new IllegalArgumentException(
                    "inputKey and outputKey must be different"
            );
        }

        Map<String, String> mappings = parseMappings(mappingsValue);

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

        List<Map<String, String>> result = new ArrayList<>();

        for (Object record : records) {
            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map"
                );
            }

            Map<String, String> mappedRow = new LinkedHashMap<>();

            RecordMapper.forEachStringEntry(row, (source, value) -> {
                String target = mappings.getOrDefault(source, source);

                if (mappedRow.containsKey(target)) {
                    throw new IllegalArgumentException(
                            "Multiple source columns map to the same target: "
                                    + target
                    );
                }

                mappedRow.put(target, value);
            });

            for (String source : mappings.keySet()) {
                if (!row.containsKey(source)) {
                    throw new IllegalArgumentException(
                            "Mapped source column not found: " + source
                    );
                }
            }

            result.add(Map.copyOf(mappedRow));
        }

        context.put(outputKey, List.copyOf(result));
    }

    private Map<String, String> parseMappings(String value) {
        Map<String, String> mappings = new LinkedHashMap<>();

        for (String pair : value.split(",", -1)) {
            String[] parts = pair.split("=", -1);

            if (parts.length != 2
                    || parts[0].isBlank()
                    || parts[1].isBlank()) {
                throw new IllegalArgumentException(
                        "Each mapping must use source=target format: " + pair
                );
            }

            String source = parts[0].trim();
            String target = parts[1].trim();

            if (mappings.containsKey(source)) {
                throw new IllegalArgumentException(
                        "Duplicate source column mapping: " + source
                );
            }

            mappings.put(source, target);
        }

        return mappings;
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