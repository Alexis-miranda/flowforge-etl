
package com.flowforge.etl.core.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    @Test
    void shouldRemoveExistingKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        Object removedValue = context.remove("name");

        assertEquals("FlowForge", removedValue);
        assertFalse(context.contains("name"));
    }

    @Test
    void shouldReturnNullWhenRemovingMissingKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        Object removedValue = context.remove("missing");

        assertNull(removedValue);
    }

    @Test
    void shouldRejectNullKeyWhenRemoving() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.remove(null)
        );
    }

    @Test
    void shouldRejectBlankKeyWhenRemoving() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.remove("   ")
        );
    }
    @Test
    void shouldRemoveAllEntriesWhenClearingContext() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");
        context.put("count", 42);

        context.clear();

        assertFalse(context.contains("name"));
        assertFalse(context.contains("count"));
        assertTrue(context.asMap().isEmpty());
    }

    @Test
    void shouldAllowStoringValuesAfterClearingContext() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        context.clear();
        context.put("status", "ready");

        assertFalse(context.contains("name"));
        assertEquals("ready", context.get("status"));
    }
/**
    @Test
    void shouldDistinguishExistingKeyWithNullValueFromMissingKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("optional", null);

        assertTrue(context.contains("optional"));
        assertNull(context.get("optional"));
        assertFalse(context.contains("missing"));
    }**/


    @Test
    void shouldRejectNullValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.put("optional", null)
        );
    }

    @Test
    void shouldReflectContextChangesInExistingMapView() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        Map<String, Object> view = context.asMap();

        context.put("name", "FlowForge");

        assertEquals("FlowForge", view.get("name"));

        context.remove("name");

        assertFalse(view.containsKey("name"));
    }
    @Test
    void shouldRejectBlankKeyWhenRetrievingTypedValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.get("   ", String.class)
        );
    }

}
