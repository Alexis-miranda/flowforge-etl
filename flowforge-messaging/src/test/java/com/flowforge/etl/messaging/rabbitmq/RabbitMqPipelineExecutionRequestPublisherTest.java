package com.flowforge.etl.messaging.rabbitmq;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RabbitMqPipelineExecutionRequestPublisherTest {

    private static final long CONFIRM_TIMEOUT_MILLIS = 5_000L;

    @Test
    void shouldPublishPersistentJsonMessageAndWaitForConfirmation() throws Exception {
        AtomicReference<Object[]> publishedArguments = new AtomicReference<>();
        List<String> calls = new java.util.ArrayList<>();
        Channel channel = channelProxy((method, arguments) -> {
            calls.add(method);
            if ("basicPublish".equals(method)) {
                publishedArguments.set(arguments);
            }
            return null;
        });

        RabbitMqPipelineExecutionRequestPublisher publisher =
                new RabbitMqPipelineExecutionRequestPublisher(
                        channel,
                        new PipelineExecutionRequestJsonCodec(),
                        "flowforge.exchange",
                        "pipeline.execute",
                        CONFIRM_TIMEOUT_MILLIS);

        UUID executionId =
                UUID.fromString("7c5b2a5c-9c36-4b6e-a1d0-19b1d0b0f4d2");
        Instant requestedAt = Instant.parse("2026-11-22T10:15:30Z");
        PipelineExecutionRequest request = new PipelineExecutionRequest(
                executionId,
                "cash",
                Map.of("sourceFile", "cash.csv"),
                requestedAt);

        publisher.publish(request);

        Object[] arguments = publishedArguments.get();
        assertEquals("flowforge.exchange", arguments[0]);
        assertEquals("pipeline.execute", arguments[1]);
        assertEquals(false, arguments[2]);

        AMQP.BasicProperties properties = (AMQP.BasicProperties) arguments[3];
        assertEquals("application/json", properties.getContentType());
        assertEquals("UTF-8", properties.getContentEncoding());
        assertEquals(2, properties.getDeliveryMode());
        assertEquals(executionId.toString(), properties.getMessageId());
        assertEquals(executionId.toString(), properties.getCorrelationId());
        assertEquals("cash", properties.getType());
        assertEquals(requestedAt.toEpochMilli(),
                properties.getTimestamp().getTime());

        String payload = new String((byte[]) arguments[4], StandardCharsets.UTF_8);
        assertEquals(request, new PipelineExecutionRequestJsonCodec()
                .deserialize(payload.getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of("confirmSelect", "basicPublish", "waitForConfirmsOrDie"),
                calls);
    }

    @Test
    void shouldRejectNullRequestBeforePublishing() throws Exception {
        Channel channel = channelProxy((method, arguments) -> null);
        RabbitMqPipelineExecutionRequestPublisher publisher =
                new RabbitMqPipelineExecutionRequestPublisher(
                        channel,
                        new PipelineExecutionRequestJsonCodec(),
                        "flowforge.exchange",
                        "pipeline.execute",
                        CONFIRM_TIMEOUT_MILLIS);

        assertThrows(NullPointerException.class, () -> publisher.publish(null));
    }

    @Test
    void shouldRejectBlankExchange() {
        Channel channel = channelProxy((method, arguments) -> null);

        assertThrows(IllegalArgumentException.class, () ->
                new RabbitMqPipelineExecutionRequestPublisher(
                        channel,
                        new PipelineExecutionRequestJsonCodec(),
                        " ",
                        "pipeline.execute",
                        CONFIRM_TIMEOUT_MILLIS));
    }

    @Test
    void shouldRejectNonPositiveConfirmTimeout() {
        Channel channel = channelProxy((method, arguments) -> null);

        assertThrows(IllegalArgumentException.class, () ->
                new RabbitMqPipelineExecutionRequestPublisher(
                        channel,
                        new PipelineExecutionRequestJsonCodec(),
                        "flowforge.exchange",
                        "pipeline.execute",
                        0));
    }

    private static Channel channelProxy(ChannelInvocation invocation) {
        return (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class},
                (proxy, method, arguments) -> {
                    if ("toString".equals(method.getName())) {
                        return "Channel test proxy";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == arguments[0];
                    }
                    return invocation.invoke(method.getName(), arguments);
                });
    }

    @FunctionalInterface
    private interface ChannelInvocation {
        Object invoke(String method, Object[] arguments);
    }
}
