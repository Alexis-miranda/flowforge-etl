package com.flowforge.etl.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

/** Delivery state of an accepted execution request, independent of worker execution state. */
public record PipelineExecutionRequestStatus(
        UUID executionId,
        String pipelineType,
        String deliveryStatus,
        Instant requestedAt,
        int publishAttempts,
        String lastError
) {
    public PipelineExecutionRequestStatus {
        if (executionId == null) {
            throw new IllegalArgumentException("Execution ID must not be null");
        }
        if (pipelineType == null || pipelineType.isBlank()) {
            throw new IllegalArgumentException("Pipeline type must not be blank");
        }
        if (!"PENDING".equals(deliveryStatus) && !"PUBLISHED".equals(deliveryStatus)) {
            throw new IllegalArgumentException("Unsupported outbox delivery status");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("Requested at must not be null");
        }
        if (publishAttempts < 0) {
            throw new IllegalArgumentException("Publish attempts must not be negative");
        }
    }
}
