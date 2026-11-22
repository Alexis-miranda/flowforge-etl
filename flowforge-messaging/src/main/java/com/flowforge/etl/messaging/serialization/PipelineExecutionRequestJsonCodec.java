package com.flowforge.etl.messaging.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.io.IOException;
import java.util.Objects;

/**
 * Serializes pipeline execution requests as JSON for transport over a message broker.
 */
public final class PipelineExecutionRequestJsonCodec {

    private final ObjectMapper objectMapper;

    public PipelineExecutionRequestJsonCodec() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Serializes a request to its UTF-8 JSON representation.
     *
     * @param request request to serialize
     * @return serialized JSON bytes
     */
    public byte[] serialize(PipelineExecutionRequest request) {
        Objects.requireNonNull(request, "Request must not be null");

        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new PipelineExecutionRequestSerializationException(
                    "Could not serialize pipeline execution request", exception);
        }
    }

    /**
     * Deserializes a UTF-8 JSON payload into a pipeline execution request.
     *
     * @param payload JSON payload
     * @return deserialized request
     */
    public PipelineExecutionRequest deserialize(byte[] payload) {
        Objects.requireNonNull(payload, "Payload must not be null");

        try {
            PipelineExecutionRequest request =
                    objectMapper.readValue(payload, PipelineExecutionRequest.class);
            if (request == null) {
                throw new PipelineExecutionRequestSerializationException(
                        "JSON payload must contain a pipeline execution request");
            }
            return request;
        } catch (IOException exception) {
            throw new PipelineExecutionRequestSerializationException(
                    "Could not deserialize pipeline execution request", exception);
        }
    }
}
