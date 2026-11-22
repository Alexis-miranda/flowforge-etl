package com.flowforge.etl.messaging.rabbitmq;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Publishes pipeline execution requests to a RabbitMQ exchange.
 *
 * <p>The supplied channel remains owned by the caller. The caller is responsible
 * for its connection lifecycle and for declaring the exchange and binding the
 * destination queue before publishing.</p>
 */
public final class RabbitMqPipelineExecutionRequestPublisher {

    private static final String CONTENT_TYPE = "application/json";
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final int PERSISTENT_DELIVERY_MODE = 2;

    private final Channel channel;
    private final PipelineExecutionRequestJsonCodec codec;
    private final String exchange;
    private final String routingKey;
    private final long confirmTimeoutMillis;

    /**
     * Creates a publisher and enables publisher confirms on the supplied channel.
     *
     * @param channel RabbitMQ channel; its lifecycle remains with the caller
     * @param codec request JSON codec
     * @param exchange target exchange
     * @param routingKey target routing key
     * @param confirmTimeoutMillis maximum time to wait for broker confirmation
     * @throws IOException if publisher confirms cannot be enabled
     */
    public RabbitMqPipelineExecutionRequestPublisher(
            Channel channel,
            PipelineExecutionRequestJsonCodec codec,
            String exchange,
            String routingKey,
            long confirmTimeoutMillis) throws IOException {

        this.channel = Objects.requireNonNull(channel, "Channel must not be null");
        this.codec = Objects.requireNonNull(codec, "Codec must not be null");
        this.exchange = requireText(exchange, "Exchange must not be blank");
        this.routingKey = requireText(routingKey, "Routing key must not be blank");

        if (confirmTimeoutMillis <= 0) {
            throw new IllegalArgumentException(
                    "Confirm timeout must be greater than zero");
        }
        this.confirmTimeoutMillis = confirmTimeoutMillis;

        this.channel.confirmSelect();
    }

    /**
     * Publishes a persistent JSON message and waits for the broker publisher
     * confirmation before returning.
     *
     * @param request execution request to publish
     * @throws IOException if publishing or broker confirmation fails
     * @throws InterruptedException if waiting for confirmation is interrupted
     * @throws TimeoutException if the broker does not confirm in time
     */
    public void publish(PipelineExecutionRequest request)
            throws IOException, InterruptedException, TimeoutException {

        Objects.requireNonNull(request, "Request must not be null");

        byte[] body = codec.serialize(request);
        String executionId = request.executionId().toString();

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType(CONTENT_TYPE)
                .contentEncoding(CONTENT_ENCODING)
                .deliveryMode(PERSISTENT_DELIVERY_MODE)
                .messageId(executionId)
                .correlationId(executionId)
                .type(request.pipelineType())
                .timestamp(Date.from(request.requestedAt()))
                .build();

        channel.basicPublish(exchange, routingKey, false, properties, body);
        channel.waitForConfirmsOrDie(confirmTimeoutMillis);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
