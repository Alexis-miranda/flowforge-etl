package com.flowforge.etl.messaging.serialization;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutionRequestJsonCodecTest {

    private final PipelineExecutionRequestJsonCodec codec =
            new PipelineExecutionRequestJsonCodec();

    @Test
    void shouldSerializeAndDeserializeRequest() {
        PipelineExecutionRequest original = new PipelineExecutionRequest(
                UUID.fromString("7c5b2a5c-9c36-4b6e-a1d0-19b1d0b0f4d2"),
                "cash",
                Map.of("sourceFile", "cash.csv", "backOfficeCode", "BNYM"),
                Instant.parse("2026-11-22T10:15:30Z")
        );

        byte[] payload = codec.serialize(original);
        PipelineExecutionRequest deserialized = codec.deserialize(payload);

        assertEquals(original, deserialized);
    }

    @Test
    void shouldSerializeRequestedAtAsIso8601String() {
        PipelineExecutionRequest request = new PipelineExecutionRequest(
                UUID.randomUUID(),
                "cash",
                Map.of(),
                Instant.parse("2026-11-22T10:15:30Z")
        );

        String json = new String(codec.serialize(request), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"requestedAt\":\"2026-11-22T10:15:30Z\""));
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThrows(PipelineExecutionRequestSerializationException.class, () ->
                codec.deserialize("{not-json".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectJsonNull() {
        assertThrows(PipelineExecutionRequestSerializationException.class, () ->
                codec.deserialize("null".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldRejectNullRequest() {
        assertThrows(NullPointerException.class, () -> codec.serialize(null));
    }

    @Test
    void shouldRejectNullPayload() {
        assertThrows(NullPointerException.class, () -> codec.deserialize(null));
    }
}
