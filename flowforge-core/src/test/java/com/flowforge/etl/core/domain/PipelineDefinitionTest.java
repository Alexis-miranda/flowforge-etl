
package com.flowforge.etl.core.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PipelineDefinition}.
 */
class PipelineDefinitionTest {

    @Test
    void shouldCreatePipelineDefinition() {
        PipelineDefinition definition = new PipelineDefinition(
                "customer-import",
                "Customer Import",
                "Imports customer records",
                List.of()
        );

        assertEquals("customer-import", definition.id());
        assertEquals("Customer Import", definition.name());
        assertEquals("Imports customer records", definition.description());
        assertTrue(definition.steps().isEmpty());
    }

    @Test
    void shouldReplaceNullDescriptionWithEmptyString() {
        PipelineDefinition definition = new PipelineDefinition(
                "customer-import",
                "Customer Import",
                null,
                List.of()
        );

        assertEquals("", definition.description());
    }

    @Test
    void shouldRejectBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition("", "Customer Import", "", List.of())
        );
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition("customer-import", " ", "", List.of())
        );
    }

    @Test
    void shouldSortStepsByExecutionOrder() {
        PipelineStep load = new PipelineStep(
                "load-customers", "Load Customers", StepType.LOAD, 3
        );
        PipelineStep extract = new PipelineStep(
                "extract-customers", "Extract Customers", StepType.EXTRACT, 1
        );
        PipelineStep transform = new PipelineStep(
                "transform-customers", "Transform Customers", StepType.TRANSFORM, 2
        );

        PipelineDefinition definition = new PipelineDefinition(
                "customer-import",
                "Customer Import",
                "",
                List.of(load, extract, transform)
        );

        assertEquals(
                List.of(extract, transform, load),
                definition.steps()
        );
    }

    @Test
    void shouldRejectDuplicateStepIds() {
        PipelineStep first = new PipelineStep(
                "step-1", "Extract", StepType.EXTRACT, 1
        );
        PipelineStep second = new PipelineStep(
                "step-1", "Load", StepType.LOAD, 2
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition(
                        "customer-import", "Customer Import", "",
                        List.of(first, second)
                )
        );
    }

    @Test
    void shouldRejectDuplicateStepOrders() {
        PipelineStep first = new PipelineStep(
                "extract", "Extract", StepType.EXTRACT, 1
        );
        PipelineStep second = new PipelineStep(
                "load", "Load", StepType.LOAD, 1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new PipelineDefinition(
                        "customer-import", "Customer Import", "",
                        List.of(first, second)
                )
        );
    }

    @Test
    void shouldKeepStepsImmutable() {
        List<PipelineStep> steps = new ArrayList<>();
        steps.add(new PipelineStep(
                "extract", "Extract", StepType.EXTRACT, 1
        ));

        PipelineDefinition definition = new PipelineDefinition(
                "customer-import", "Customer Import", "", steps
        );

        steps.clear();

        assertEquals(1, definition.steps().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.steps().clear()
        );
    }

    @Test
    void shouldRejectNullSteps() {
        assertThrows(
                NullPointerException.class,
                () -> new PipelineDefinition(
                        "customer-import", "Customer Import", "", null
                )
        );
    }
}

