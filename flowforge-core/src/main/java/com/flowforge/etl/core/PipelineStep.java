
package com.flowforge.etl.core;

import java.util.Objects;

/**
 * Represents an individual step in an ETL pipeline definition.
 *
 * @param id unique identifier of the step
 * @param name human-readable name of the step
 * @param type category of the ETL operation
 * @param order execution position, starting at one
 * @param configuration parameters required by the step
 */
public record PipelineStep(
        String id,
        String name,
        StepType type,
        int order,
        StepConfiguration configuration
) {

    /**
     * Creates a pipeline step with an empty configuration.
     *
     * @param id unique identifier of the step
     * @param name human-readable name of the step
     * @param type category of the ETL operation
     * @param order execution position, starting at one
     */
    public PipelineStep(String id, String name, StepType type, int order) {
        this(id, name, type, order, new StepConfiguration(java.util.Map.of()));
    }

    /**
     * Validates the mandatory properties of a pipeline step.
     */
    public PipelineStep {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Step id must not be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Step name must not be blank");
        }

        Objects.requireNonNull(type, "Step type must not be null");
        Objects.requireNonNull(
                configuration,
                "Step configuration must not be null"
        );

        if (order < 1) {
            throw new IllegalArgumentException(
                    "Step order must be greater than or equal to one"
            );
        }
    }
}
