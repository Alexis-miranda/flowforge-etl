
package com.flowforge.etl.core;

import java.util.Objects;

/**
 * Represents the configuration and metadata of an ETL pipeline.
 *
 * @param id unique identifier of the pipeline
 * @param name human-readable name of the pipeline
 * @param description optional description of the pipeline
 */
public record PipelineDefinition(
        String id,
        String name,
        String description
) {

    /**
     * Validates the mandatory pipeline properties.
     */
    public PipelineDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Pipeline id must not be blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Pipeline name must not be blank");
        }

        description = Objects.requireNonNullElse(description, "");
    }
}
