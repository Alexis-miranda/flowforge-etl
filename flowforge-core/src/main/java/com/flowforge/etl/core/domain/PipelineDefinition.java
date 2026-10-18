
package com.flowforge.etl.core.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the configuration and metadata of an ETL pipeline.
 *
 * @param id unique identifier of the pipeline
 * @param name human-readable name of the pipeline
 * @param description optional description of the pipeline
 * @param steps ordered steps that make up the pipeline
 */
public record PipelineDefinition(
        String id,
        String name,
        String description,
        List<PipelineStep> steps
) {

    /**
     * Validates the pipeline properties and creates an immutable step list.
     */
    public PipelineDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pipeline id must not be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pipeline name must not be blank");
        }

        description = Objects.requireNonNullElse(description, "");

        Objects.requireNonNull(steps, "Pipeline steps must not be null");
        steps = List.copyOf(steps);

        Set<String> stepIds = new HashSet<>();
        Set<Integer> stepOrders = new HashSet<>();

        for (PipelineStep step : steps) {
            Objects.requireNonNull(step, "Pipeline steps must not contain null");

            if (!stepIds.add(step.id())) {
                throw new IllegalArgumentException(
                        "Pipeline step ids must be unique: " + step.id()
                );
            }

            if (!stepOrders.add(step.order())) {
                throw new IllegalArgumentException(
                        "Pipeline step orders must be unique: " + step.order()
                );
            }
        }

        steps = steps.stream()
                .sorted(java.util.Comparator.comparingInt(PipelineStep::order))
                .toList();
    }
}
