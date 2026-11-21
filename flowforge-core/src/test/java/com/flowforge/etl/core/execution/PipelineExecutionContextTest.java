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
    void shouldRetrieveValueUsingExpectedType() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        String name = context.get("name", String.class);

        assertEquals("FlowForge", name);
    }

    @Test
    void shouldReturnNullWhenTypedValueDoesNotExist() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertNull(context.get("missing", String.class));
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
    void shouldRejectNullExpectedTypeWhenUsingTypedGet() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.get("name", null)
        );
    }

    @Test
    void shouldRejectBlankKeyWhenRetrievingTypedValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.get("   ", String.class)
        );
    }

    @Test
    void shouldRetrieveRequiredValueUsingExpectedType() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        String name = context.getRequired("name", String.class);

        assertEquals("FlowForge", name);
    }

    @Test
    void shouldRejectMissingRequiredValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.getRequired("missing", String.class)
        );

        assertEquals(
                "Missing required context value: missing",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRequiredValueWithIncompatibleType() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("count", 42);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> context.getRequired("count", String.class)
        );

        assertEquals(
                "Context value for key 'count' must be of type String",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullExpectedTypeWhenUsingRequiredGet() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.getRequired("name", null)
        );
    }

    @Test
    void shouldRejectBlankKeyWhenRetrievingRequiredValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.getRequired("   ", String.class)
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
    void shouldRemoveExistingKeyAndReturnItsValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("name", "FlowForge");

        Object removedValue = context.remove("name");

        assertEquals("FlowForge", removedValue);
        assertFalse(context.contains("name"));
        assertNull(context.get("name"));
    }

    @Test
    void shouldReturnNullWhenRemovingMissingKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertNull(context.remove("missing"));
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

    @Test
    void shouldRejectNullValue() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        assertThrows(
                IllegalArgumentException.class,
                () -> context.put("optional", null)
        );
    }

    @Test
    void shouldExposeUnmodifiableLiveMapView() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        Map<String, Object> view = context.asMap();

        context.put("name", "FlowForge");

        assertEquals("FlowForge", view.get("name"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> view.put("status", "failed")
        );

        context.remove("name");

        assertFalse(view.containsKey("name"));
    }
}
