
package com.flowforge.etl.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StepConfiguration}.
 */
class StepConfigurationTest {

    @Test
    void shouldStoreConfigurationProperties() {
        StepConfiguration configuration = new StepConfiguration(
                Map.of(
                        "query", "SELECT * FROM customers",
                        "fetchSize", "500"
                )
        );

        assertEquals(
                "SELECT * FROM customers",
                configuration.get("query")
        );
        assertEquals("500", configuration.get("fetchSize"));
    }

    @Test
    void shouldReturnNullForMissingProperty() {
        StepConfiguration configuration =
                new StepConfiguration(Map.of());

        assertEquals(null, configuration.get("missing"));
        assertFalse(configuration.contains("missing"));
    }

    @Test
    void shouldCheckWhetherPropertyExists() {
        StepConfiguration configuration = new StepConfiguration(
                Map.of("targetTable", "customers")
        );

        assertTrue(configuration.contains("targetTable"));
    }

    @Test
    void shouldRejectNullPropertiesMap() {
        assertThrows(
                NullPointerException.class,
                () -> new StepConfiguration(null)
        );
    }

    @Test
    void shouldRejectBlankPropertyName() {
        Map<String, String> properties = new HashMap<>();
        properties.put(" ", "value");

        assertThrows(
                IllegalArgumentException.class,
                () -> new StepConfiguration(properties)
        );
    }

    @Test
    void shouldRejectNullPropertyValue() {
        Map<String, String> properties = new HashMap<>();
        properties.put("query", null);

        assertThrows(
                NullPointerException.class,
                () -> new StepConfiguration(properties)
        );
    }

    @Test
    void shouldKeepPropertiesImmutable() {
        Map<String, String> properties = new HashMap<>();
        properties.put("fetchSize", "500");

        StepConfiguration configuration =
                new StepConfiguration(properties);

        properties.put("fetchSize", "1000");

        assertEquals("500", configuration.get("fetchSize"));

        assertThrows(
                UnsupportedOperationException.class,
                () -> configuration.properties().put("fetchSize", "2000")
        );
    }
}
