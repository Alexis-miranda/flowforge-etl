package com.flowforge.etl.messaging.rabbitmq;

import java.util.Objects;

/**
 * Broker topology and bounded retry settings for a request consumer.
 *
 * <p>The retry queue must use a message TTL and dead-letter back to the main
 * queue. The dead-letter queue is the terminal destination for poison and
 * exhausted messages.</p>
 */
public record RabbitMqRetryPolicy(
        String retryQueue,
        String deadLetterQueue,
        int maxRetries,
        long retryDelayMillis,
        long confirmTimeoutMillis) {

    public RabbitMqRetryPolicy {
        retryQueue = requireText(retryQueue, "Retry queue must not be blank");
        deadLetterQueue = requireText(deadLetterQueue, "Dead-letter queue must not be blank");
        if (retryQueue.equals(deadLetterQueue)) {
            throw new IllegalArgumentException("Retry and dead-letter queues must differ");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Maximum retries must not be negative");
        }
        if (retryDelayMillis < 1) {
            throw new IllegalArgumentException("Retry delay must be positive");
        }
        if (confirmTimeoutMillis < 1) {
            throw new IllegalArgumentException("Confirm timeout must be positive");
        }
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
