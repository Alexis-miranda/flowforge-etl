
package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

/**
 * Transforms record values to uppercase.
 */
public class UppercaseTransformer implements StepExecutor {

    /**
     * Returns the type of step supported by this executor.
     *
     * @return the TRANSFORM step type
     */
    @Override
    public StepType supportedType() {
        return StepType.TRANSFORM;
    }

    @Override
    public String connectorKey() {
        return "uppercase";
    }
    /**
     * Executes the uppercase transformation.
     *
     * @param step the pipeline step to execute
     * @param context the shared pipeline execution context
     */
    @Override
    public void execute(
            PipelineStep step,
            PipelineExecutionContext context
    ) {
        String inputKey = getRequiredProperty(step, "inputKey");
        String outputKey = getRequiredProperty(step, "outputKey");
        String column = getRequiredProperty(step, "column");

        if (!context.contains(inputKey)) {
            throw new IllegalArgumentException(
                    "Missing input data in execution context: " + inputKey
            );
        }

        Object input = context.get(inputKey);

        if (!(input instanceof java.util.List<?> records)) {
            throw new IllegalArgumentException(
                    "Input data must be a list of records: " + inputKey
            );
        }

        java.util.List<java.util.Map<String, String>> transformedRecords =
                new java.util.ArrayList<>();

        for (Object record : records) {
            if (!(record instanceof java.util.Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map"
                );
            }

            java.util.Map<String, String> transformedRow =
                    new java.util.LinkedHashMap<>();

            RecordMapper.forEachStringEntry(row, (key, value) -> {
                if (key.equals(column)) {
                    transformedRow.put(
                            key,
                            value.toUpperCase(java.util.Locale.ROOT)
                    );
                } else {
                    transformedRow.put(key, value);
                }
            });

            if (!transformedRow.containsKey(column)) {
                throw new IllegalArgumentException(
                        "Column not found in input record: " + column
                );
            }

            transformedRecords.add(java.util.Map.copyOf(transformedRow));
        }

        context.put(outputKey, java.util.List.copyOf(transformedRecords));
    }
    /**
     * Retrieves a required configuration property.
     *
     * @param step the pipeline step containing the configuration
     * @param name the name of the required property
     * @return the property's non-blank value
     * @throws IllegalArgumentException if the property is missing or blank
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
}
