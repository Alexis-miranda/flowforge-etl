
package com.flowforge.etl.core;

import java.util.Map;
import java.util.Objects;

/**
 * Represents the configuration parameters of an ETL pipeline step.
 *
 * @param properties configuration properties indexed by their names
 */
public record StepConfiguration(Map<String, String> properties) {

    /**
     * Validates and stores an immutable copy of the configuration properties.
     */
    public StepConfiguration {
        Objects.requireNonNull(
                properties,
                "Configuration properties must not be null"
        );

        properties.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "Configuration property names must not be blank"
                );
            }

            Objects.requireNonNull(
                    value,
                    "Configuration property values must not be null"
            );
        });

        properties = Map.copyOf(properties);
    }

    /**
     * Returns the value of a configuration property.
     *
     * @param name property name
     * @return the property value, or null if the property is not present
     */
    public String get(String name) {
        return properties.get(name);
    }

    /**
     * Checks whether a configuration property exists.
     *
     * @param name property name
     * @return true if the property exists
     */
    public boolean contains(String name) {
        return properties.containsKey(name);
    }
}
