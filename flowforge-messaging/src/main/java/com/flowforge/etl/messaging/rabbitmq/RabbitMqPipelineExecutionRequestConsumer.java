package com.flowforge.etl.messaging.rabbitmq;

import com.flowforge.etl.messaging.consumer.NonRetryablePipelineExecutionRequestException;
import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestHandler;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Consumes pipeline execution requests using manual acknowledgements.
 *
 * <p>When a retry policy is supplied, transient handler failures are republished
 * to a delayed retry queue and acknowledged only after publisher confirmation.
 * The retry queue must dead-letter back to the main queue after its TTL. Messages
 * that are malformed, non-retryable, or have exhausted their retry budget are
 * routed to the configured dead-letter queue. If a republish cannot be
 * confirmed, the original delivery is requeued.</p>
 *
 * <p>The supplied channel remains owned by the caller. This consumer does not
 * create a connection, declare broker topology, or close the supplied channel.</p>
 */
public final class RabbitMqPipelineExecutionRequestConsumer implements AutoCloseable {

    private static final String RETRY_HEADER = "x-flowforge-retry-count";

    private final Channel channel;
    private final PipelineExecutionRequestJsonCodec codec;
    private final PipelineExecutionRequestHandler handler;
    private final String queue;
    private final int prefetchCount;
    private final RabbitMqRetryPolicy retryPolicy;
    private String consumerTag;

    /**
     * Creates a consumer with legacy broker requeue behavior.
     * Prefer the overload that accepts a bounded retry policy in worker deployments.
     */
    public RabbitMqPipelineExecutionRequestConsumer(
            Channel channel,
            PipelineExecutionRequestJsonCodec codec,
            PipelineExecutionRequestHandler handler,
            String queue,
            int prefetchCount) {
        this(channel, codec, handler, queue, prefetchCount, null);
    }

    public RabbitMqPipelineExecutionRequestConsumer(
            Channel channel,
            PipelineExecutionRequestJsonCodec codec,
            PipelineExecutionRequestHandler handler,
            String queue,
            int prefetchCount,
            RabbitMqRetryPolicy retryPolicy) {
        this.channel = Objects.requireNonNull(channel, "Channel must not be null");
        this.codec = Objects.requireNonNull(codec, "Codec must not be null");
        this.handler = Objects.requireNonNull(handler, "Handler must not be null");
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("Queue must not be blank");
        }
        if (prefetchCount < 1) {
            throw new IllegalArgumentException("Prefetch count must be greater than zero");
        }
        if (retryPolicy != null
                && (queue.equals(retryPolicy.retryQueue())
                || queue.equals(retryPolicy.deadLetterQueue()))) {
            throw new IllegalArgumentException(
                    "Main, retry, and dead-letter queues must have different names");
        }
        this.queue = queue;
        this.prefetchCount = prefetchCount;
        this.retryPolicy = retryPolicy;
    }

    /** Starts manual-ack consumption. The queue topology is managed by the caller. */
    public synchronized String start() throws IOException {
        if (consumerTag != null) {
            throw new IllegalStateException("Consumer has already been started");
        }
        channel.basicQos(prefetchCount);
        DeliverCallback deliverCallback = this::handleDelivery;
        CancelCallback cancelCallback = tag -> {
            synchronized (RabbitMqPipelineExecutionRequestConsumer.this) {
                if (Objects.equals(consumerTag, tag)) {
                    consumerTag = null;
                }
            }
        };
        consumerTag = channel.basicConsume(queue, false, deliverCallback, cancelCallback);
        return consumerTag;
    }

    private void handleDelivery(String ignoredConsumerTag, Delivery delivery)
            throws IOException {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        final PipelineExecutionRequest request;
        try {
            request = codec.deserialize(delivery.getBody());
        } catch (RuntimeException malformedMessage) {
            routeToDeadLetterOrReject(deliveryTag, delivery);
            return;
        }

        try {
            handler.handle(request);
        } catch (NonRetryablePipelineExecutionRequestException invalidRequest) {
            routeToDeadLetterOrReject(deliveryTag, delivery);
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            channel.basicNack(deliveryTag, false, true);
            return;
        } catch (Exception processingFailure) {
            routeForRetryOrDeadLetter(deliveryTag, delivery);
            return;
        }

        channel.basicAck(deliveryTag, false);
    }

    private void routeToDeadLetterOrReject(long deliveryTag, Delivery delivery)
            throws IOException {
        if (retryPolicy == null) {
            channel.basicReject(deliveryTag, false);
            return;
        }
        publishThenAck(deliveryTag, delivery, retryPolicy.deadLetterQueue(),
                currentRetryCount(delivery.getProperties().getHeaders()));
    }

    private void routeForRetryOrDeadLetter(long deliveryTag, Delivery delivery)
            throws IOException {
        if (retryPolicy == null) {
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        int retryCount = currentRetryCount(delivery.getProperties().getHeaders());
        if (retryCount < 0 || retryCount >= retryPolicy.maxRetries()) {
            publishThenAck(deliveryTag, delivery, retryPolicy.deadLetterQueue(), retryCount);
            return;
        }

        publishThenAck(deliveryTag, delivery, retryPolicy.retryQueue(), retryCount + 1);
    }

    /**
     * Publishes the original body before acknowledging it. A failed or uncertain
     * publish leaves the original delivery unacknowledged and requests redelivery.
     */
    private void publishThenAck(
            long deliveryTag,
            Delivery delivery,
            String destinationQueue,
            int retryCount) throws IOException {
        try {
            Map<String, Object> headers = new HashMap<>();
            Map<String, Object> existingHeaders = delivery.getProperties().getHeaders();
            if (existingHeaders != null) {
                headers.putAll(existingHeaders);
            }
            if (retryCount >= 0) {
                headers.put(RETRY_HEADER, retryCount);
            }

            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType(delivery.getProperties().getContentType())
                    .contentEncoding(delivery.getProperties().getContentEncoding())
                    .deliveryMode(2)
                    .messageId(delivery.getProperties().getMessageId())
                    .correlationId(delivery.getProperties().getCorrelationId())
                    .type(delivery.getProperties().getType())
                    .timestamp(delivery.getProperties().getTimestamp())
                    .headers(headers)
                    .build();

            channel.basicPublish("", destinationQueue, false, properties, delivery.getBody());
            channel.waitForConfirmsOrDie(retryPolicy.confirmTimeoutMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            channel.basicNack(deliveryTag, false, true);
            return;
        } catch (TimeoutException | IOException | RuntimeException publishFailure) {
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        // Keep acknowledgement outside the publish catch: an ACK failure must
        // not be followed by a NACK on the same delivery.
        channel.basicAck(deliveryTag, false);
    }

    private static int currentRetryCount(Map<String, Object> headers) {
        if (headers == null || !headers.containsKey(RETRY_HEADER)) {
            return 0;
        }
        Object value = headers.get(RETRY_HEADER);
        if (value instanceof Number number) {
            long count = number.longValue();
            return count < 0 || count > Integer.MAX_VALUE ? -1 : (int) count;
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    /** Cancels this consumer without closing the caller-owned channel. */
    @Override
    public synchronized void close() throws IOException {
        if (consumerTag == null) {
            return;
        }
        String activeTag = consumerTag;
        consumerTag = null;
        channel.basicCancel(activeTag);
    }
}
