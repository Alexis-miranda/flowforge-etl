
package com.flowforge.etl.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PipelineStep}.
 */
class PipelineStepTest {

    @Test
    void shouldCreatePipelineStep() {
        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract Customers",
                StepType.EXTRACT,
                1
        );

        assertEquals("extract-customers", step.id());
        assertEquals("Extract Customers", step.name());
        assertEquals(StepType.EXTRACT, step.type());
        assertEquals(1, step.order());
    }

    @Test
    void shouldRejectBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineStep("", "Extract Customers",
                        StepType.EXTRACT, 1)
        );
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineStep("extract-customers", " ",
                        StepType.EXTRACT, 1)
        );
    }

    @Test
    void shouldRejectNullType() {
        assertThrows(
                NullPointerException.class,
                () -> new PipelineStep("extract-customers",
                        "Extract Customers", null, 1)
        );
    }

    @Test
    void shouldRejectInvalidOrder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineStep("extract-customers",
                        "Extract Customers", StepType.EXTRACT, 0)
        );
    }

    @Test
    void shouldCreateStepWithEmptyConfigurationByDefault() {
        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract Customers",
                StepType.EXTRACT,
                1
        );

        assertTrue(step.configuration().properties().isEmpty());
    }

    @Test
    void shouldCreateStepWithCustomConfiguration() {
        StepConfiguration configuration = new StepConfiguration(
                java.util.Map.of(
                        "query", "SELECT * FROM customers",
                        "fetchSize", "500"
                )
        );

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract Customers",
                StepType.EXTRACT,
                1,
                configuration
        );

        assertEquals(
                "SELECT * FROM customers",
                step.configuration().get("query")
        );
        assertEquals("500", step.configuration().get("fetchSize"));
    }

    @Test
    void shouldRejectNullConfiguration() {
        assertThrows(
                NullPointerException.class,
                () -> new PipelineStep(
                        "extract-customers",
                        "Extract Customers",
                        StepType.EXTRACT,
                        1,
                        null
                )
        );
    }

}
