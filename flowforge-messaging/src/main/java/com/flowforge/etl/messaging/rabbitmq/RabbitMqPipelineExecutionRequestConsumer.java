package com.flowforge.etl.messaging.rabbitmq;

import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestHandler;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestSerializationException;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.Objects;

/**
 * Consumes pipeline execution requests using manual acknowledgements.
 *
 * <p>The queue must be declared and bound by the application before start.
 * The supplied channel remains owned by the caller. Malformed messages are
 * rejected without requeue; handler failures are negatively acknowledged with
 * requeue enabled. Configure a dead-letter exchange on the queue if rejected
 * poison messages must be retained for inspection.</p>
 *
 * <p>This consumer does not create a connection, declare broker topology,
 * schedule retries, or close the supplied channel.</p>
 */
public final class RabbitMqPipelineExecutionRequestConsumer implements AutoCloseable {

    private final Channel channel;
    private final PipelineExecutionRequestJsonCodec codec;
    private final PipelineExecutionRequestHandler handler;
    private final String queue;
    private final int prefetchCount;
    private String consumerTag;

    public RabbitMqPipelineExecutionRequestConsumer(
            Channel channel,
            PipelineExecutionRequestJsonCodec codec,
            PipelineExecutionRequestHandler handler,
            String queue,
            int prefetchCount) {
        this.channel = Objects.requireNonNull(channel, "Channel must not be null");
        this.codec = Objects.requireNonNull(codec, "Codec must not be null");
        this.handler = Objects.requireNonNull(handler, "Handler must not be null");
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("Queue must not be blank");
        }
        if (prefetchCount < 1) {
            throw new IllegalArgumentException("Prefetch count must be greater than zero");
        }
        this.queue = queue;
        this.prefetchCount = prefetchCount;
    }

    /**
     * Starts manual-ack consumption. The queue topology is managed by the caller.
     *
     * @return RabbitMQ consumer tag
     * @throws IOException if QoS or consumer registration fails
     */
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
        } catch (PipelineExecutionRequestSerializationException malformedMessage) {
            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            handler.handle(request);
            channel.basicAck(deliveryTag, false);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            channel.basicNack(deliveryTag, false, true);
        } catch (Exception processingFailure) {
            channel.basicNack(deliveryTag, false, true);
        }
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
