package com.flowforge.etl.messaging.outbox;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A persisted request waiting to be delivered through the messaging transport. */
public record PipelineExecutionOutboxMessage(
        UUID messageId,
        PipelineExecutionRequest request,
        Instant createdAt,
        int publishAttempts,
        String lastError
) {
    public PipelineExecutionOutboxMessage {
        Objects.requireNonNull(messageId, "Message ID must not be null");
        Objects.requireNonNull(request, "Request must not be null");
        if (!messageId.equals(request.executionId())) {
            throw new IllegalArgumentException(
                    "Message ID must match the request execution ID");
        }
        Objects.requireNonNull(createdAt, "Created at must not be null");
        if (publishAttempts < 0) {
            throw new IllegalArgumentException("Publish attempts must not be negative");
        }
    }
}
