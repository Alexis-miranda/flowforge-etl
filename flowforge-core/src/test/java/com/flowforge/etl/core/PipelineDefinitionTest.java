
package com.flowforge.etl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link PipelineDefinition}.
 */
class PipelineDefinitionTest {

    @Test
    void shouldCreatePipelineDefinition() {
        PipelineDefinition definition = new PipelineDefinition(
                "customer-import",
                "Customer Import",
                "Imports customer records"
        );

        assertEquals("customer-import", definition.id());
        assertEquals("Customer Import", definition.name());
        assertEquals("Imports customer records", definition.description());
    }

    @Test
    void shouldReplaceNullDescriptionWithEmptyString() {
        PipelineDefinition definition = new PipelineDefinition(
                "customer-import",
                "Customer Import",
                null
        );

        assertEquals("", definition.description());
    }

    @Test
    void shouldRejectBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition("", "Customer Import", "")
        );
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition("customer-import", " ", "")
        );
    }
}
