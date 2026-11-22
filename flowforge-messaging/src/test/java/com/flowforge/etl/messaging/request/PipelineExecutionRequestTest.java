package com.flowforge.etl.messaging.request;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionRequestTest {

    @Test
    void shouldCreateRequestWithValidValues() {
        UUID executionId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-11-22T10:15:30Z");
        Map<String, String> parameters = Map.of(
                "sourceFile", "cash.csv",
                "backOfficeCode", "BNYM"
        );

        PipelineExecutionRequest request = new PipelineExecutionRequest(
                executionId, "cash", parameters, requestedAt);

        assertEquals(executionId, request.executionId());
        assertEquals("cash", request.pipelineType());
        assertEquals(parameters, request.parameters());
        assertEquals(requestedAt, request.requestedAt());
    }

    @Test
    void shouldRejectNullExecutionId() {
        assertThrows(NullPointerException.class, () ->
                new PipelineExecutionRequest(
                        null, "cash", Map.of(), Instant.now()));
    }

    @Test
    void shouldRejectBlankPipelineType() {
        assertThrows(IllegalArgumentException.class, () ->
                new PipelineExecutionRequest(
                        UUID.randomUUID(), " ", Map.of(), Instant.now()));
    }

    @Test
    void shouldRejectNullParameters() {
        assertThrows(NullPointerException.class, () ->
                new PipelineExecutionRequest(
                        UUID.randomUUID(), "cash", null, Instant.now()));
    }

    @Test
    void shouldRejectBlankParameterKeys() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(" ", "value");

        assertThrows(IllegalArgumentException.class, () ->
                new PipelineExecutionRequest(
                        UUID.randomUUID(), "cash", parameters, Instant.now()));
    }

    @Test
    void shouldRejectNullRequestedAt() {
        assertThrows(NullPointerException.class, () ->
                new PipelineExecutionRequest(
                        UUID.randomUUID(), "cash", Map.of(), null));
    }

    @Test
    void shouldDefensivelyCopyAndExposeImmutableParameters() {
        Map<String, String> original = new HashMap<>();
        original.put("sourceFile", "cash.csv");

        PipelineExecutionRequest request = new PipelineExecutionRequest(
                UUID.randomUUID(), "cash", original, Instant.now());

        original.put("sourceFile", "changed.csv");

        assertEquals("cash.csv", request.parameters().get("sourceFile"));
        assertThrows(UnsupportedOperationException.class, () ->
                request.parameters().put("backOfficeCode", "BNYM"));
        assertTrue(request.parameters().containsKey("sourceFile"));
    }
}
