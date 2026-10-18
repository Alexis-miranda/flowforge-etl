
package com.flowforge.etl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
