package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

/**
 * Application boundary for processing a decoded pipeline execution request.
 *
 * <p>Implementations must be idempotent for a given execution ID because
 * RabbitMQ redelivery is possible when a worker completes processing but
 * cannot acknowledge the delivery.</p>
 */
@FunctionalInterface
public interface PipelineExecutionRequestHandler {

    /** Processes one request. Failures are propagated so the consumer can requeue it. */
    void handle(PipelineExecutionRequest request) throws Exception;
}
