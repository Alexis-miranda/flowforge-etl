package com.flowforge.etl.messaging.rabbitmq;

import com.flowforge.etl.messaging.consumer.NonRetryablePipelineExecutionRequestException;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqPipelineExecutionRequestConsumerTest {

    private static final PipelineExecutionRequest REQUEST =
            new PipelineExecutionRequest(
                    UUID.fromString("7c5b2a5c-9c36-4b6e-a1d0-19b1d0b0f4d2"),
                    "cash",
                    Map.of("sourceFile", "cash.csv"),
                    Instant.parse("2026-11-22T10:15:30Z"));

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void shouldUseManualAcknowledgementsAndAckSuccessfulProcessing() throws Exception {
        AtomicReference<DeliverCallback> callback = new AtomicReference<>();
        List<String> calls = new ArrayList<>();
        Channel channel = channelProxy((method, args) -> {
            calls.add(method);
            if ("basicConsume".equals(method)) {
                assertEquals("flowforge.requests", args[0]);
                assertEquals(false, args[1]);
                callback.set((DeliverCallback) args[2]);
                return "consumer-1";
            }
            return null;
        });
        List<PipelineExecutionRequest> handled = new ArrayList<>();
        RabbitMqPipelineExecutionRequestConsumer consumer =
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        handled::add, "flowforge.requests", 4);

        assertEquals("consumer-1", consumer.start());
        callback.get().handle("consumer-1", delivery(41,
                new PipelineExecutionRequestJsonCodec().serialize(REQUEST)));

        assertEquals(List.of(REQUEST), handled);
        assertTrue(calls.contains("basicQos"));
        assertTrue(calls.contains("basicAck"));
        assertFalse(calls.contains("basicNack"));
        assertFalse(calls.contains("basicReject"));
        consumer.close();
        assertTrue(calls.contains("basicCancel"));
    }

    @Test
    void shouldRejectMalformedPayloadWithoutRequeue() throws Exception {
        AtomicReference<DeliverCallback> callback = new AtomicReference<>();
        AtomicReference<Object[]> rejectArguments = new AtomicReference<>();
        Channel channel = channelProxy((method, args) -> {
            if ("basicConsume".equals(method)) {
                callback.set((DeliverCallback) args[2]);
                return "consumer-1";
            }
            if ("basicReject".equals(method)) {
                rejectArguments.set(args);
            }
            return null;
        });
        RabbitMqPipelineExecutionRequestConsumer consumer =
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        request -> fail("Malformed request must not reach handler"),
                        "flowforge.requests", 1);

        consumer.start();
        callback.get().handle("consumer-1", delivery(42, "{not-json".getBytes(StandardCharsets.UTF_8)));

        assertEquals(42L, rejectArguments.get()[0]);
        assertEquals(false, rejectArguments.get()[1]);
    }

    @Test
    void shouldRequeueWhenHandlerFails() throws Exception {
        AtomicReference<DeliverCallback> callback = new AtomicReference<>();
        AtomicReference<Object[]> nackArguments = new AtomicReference<>();
        Channel channel = channelProxy((method, args) -> {
            if ("basicConsume".equals(method)) {
                callback.set((DeliverCallback) args[2]);
                return "consumer-1";
            }
            if ("basicNack".equals(method)) {
                nackArguments.set(args);
            }
            return null;
        });
        RabbitMqPipelineExecutionRequestConsumer consumer =
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        request -> { throw new IllegalStateException("temporary worker failure"); },
                        "flowforge.requests", 1);

        consumer.start();
        callback.get().handle("consumer-1", delivery(43,
                new PipelineExecutionRequestJsonCodec().serialize(REQUEST)));

        assertEquals(43L, nackArguments.get()[0]);
        assertEquals(false, nackArguments.get()[1]);
        assertEquals(true, nackArguments.get()[2]);
    }

    @Test
    void shouldRestoreInterruptFlagAndRequeueWhenHandlerIsInterrupted() throws Exception {
        AtomicReference<DeliverCallback> callback = new AtomicReference<>();
        AtomicReference<Object[]> nackArguments = new AtomicReference<>();
        Channel channel = channelProxy((method, args) -> {
            if ("basicConsume".equals(method)) {
                callback.set((DeliverCallback) args[2]);
                return "consumer-1";
            }
            if ("basicNack".equals(method)) {
                nackArguments.set(args);
            }
            return null;
        });
        RabbitMqPipelineExecutionRequestConsumer consumer =
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        request -> { throw new InterruptedException("shutdown"); },
                        "flowforge.requests", 1);

        consumer.start();
        callback.get().handle("consumer-1", delivery(44,
                new PipelineExecutionRequestJsonCodec().serialize(REQUEST)));

        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(44L, nackArguments.get()[0]);
        assertEquals(true, nackArguments.get()[2]);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        Channel channel = channelProxy((method, args) -> null);
        assertThrows(IllegalArgumentException.class, () ->
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        request -> { }, " ", 1));
        assertThrows(IllegalArgumentException.class, () ->
                new RabbitMqPipelineExecutionRequestConsumer(
                        channel, new PipelineExecutionRequestJsonCodec(),
                        request -> { }, "flowforge.requests", 0));
    }

    private static Delivery delivery(long tag, byte[] body) {
        return new Delivery(
                new Envelope(tag, false, "flowforge.exchange", "pipeline.execute"),
                new AMQP.BasicProperties.Builder().contentType("application/json").build(),
                body);
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
        Object invoke(String method, Object[] arguments) throws Throwable;
    }
}
