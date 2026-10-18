
package com.flowforge.etl.core.execution;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
