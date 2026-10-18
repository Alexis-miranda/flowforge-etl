
package com.flowforge.etl.core.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutionContextTest {

    @Test
    void shouldStoreAndRetrieveValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("records", List.of("record-1", "record-2"));

        assertEquals(
                List.of("record-1", "record-2"),
                context.get("records")
        );
    }

    @Test
    void shouldReturnNullForUnknownKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertNull(context.get("unknown"));
    }

    @Test
    void shouldRejectBlankKeyWhenStoring() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.put(" ", "value")
        );
    }

    @Test
    void shouldRejectNullKeyWhenRetrieving() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.get(null)
        );
    }

    @Test
    void shouldExposeUnmodifiableMap() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("status", "running");

        assertThrows(
                UnsupportedOperationException.class,
                () -> context.asMap().put("status", "failed")
        );
    }
    @Test
    void shouldRetrieveValueUsingExpectedType() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        String name = context.get("name", String.class);

        assertEquals("FlowForge", name);
    }

    @Test
    void shouldReturnNullWhenTypedValueDoesNotExist() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        String value = context.get("missing", String.class);

        assertNull(value);
    }

    @Test
    void shouldRejectValueWithIncompatibleType() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("count", 42);

        assertThrows(
                ClassCastException.class,
                () -> context.get("count", String.class)
        );
    }

    @Test
    void shouldRejectNullExpectedType() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.get("name", null)
        );
    }
    @Test
    void shouldReturnTrueWhenKeyExists() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("records", List.of("record-1", "record-2"));

        assertTrue(context.contains("records"));
    }

    @Test
    void shouldReturnFalseWhenKeyDoesNotExist() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertFalse(context.contains("missing"));
    }

    @Test
    void shouldRejectNullKeyWhenCheckingExistence() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.contains(null)
        );
    }
}
