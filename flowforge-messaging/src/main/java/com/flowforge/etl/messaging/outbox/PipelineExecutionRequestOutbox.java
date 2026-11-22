package com.flowforge.etl.messaging.outbox;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable store for accepted pipeline requests and their delivery state.
 *
 * <p>{@link #enqueue(PipelineExecutionRequest)} must persist the request and
 * its outbox entry atomically. Re-enqueuing the same execution ID and identical
 * request is idempotent; reusing the ID for different request content is an error.</p>
 */
public interface PipelineExecutionRequestOutbox {

    /**
     * Finds the accepted request and its current broker-delivery state.
     * Repositories that do not expose request lookup may keep the default empty result.
     */
    default Optional<PipelineExecutionRequestStatus> findRequestStatus(UUID executionId) {
        return Optional.empty();
    }

    /** Atomically stores a request and its pending delivery record. */
    void enqueue(PipelineExecutionRequest request);

    /** Returns pending messages in creation order, up to the requested batch size. */
    List<PipelineExecutionOutboxMessage> findPending(int batchSize);

    /** Marks a pending message as published; returns false if it is no longer pending. */
    boolean markPublished(UUID messageId, Instant publishedAt);

    /** Records a failed publish attempt and makes the message available for retry. */
    boolean recordPublishFailure(UUID messageId, String errorMessage);
}
