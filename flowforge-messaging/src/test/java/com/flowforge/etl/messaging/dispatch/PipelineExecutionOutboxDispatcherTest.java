package com.flowforge.etl.messaging.dispatch;

import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutionOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void shouldMarkMessagePublishedOnlyAfterPublisherSucceeds() {
        FakeOutbox outbox = new FakeOutbox(List.of(message()));
        PipelineExecutionOutboxDispatcher dispatcher =
                new PipelineExecutionOutboxDispatcher(
                        outbox, request -> { }, clock);

        OutboxDispatchResult result = dispatcher.dispatchOnce(10);

        assertEquals(new OutboxDispatchResult(1, 1, 0, 0), result);
        assertEquals(List.of(messageId), outbox.markedPublished);
        assertEquals(now, outbox.publishedAt);
        assertTrue(outbox.recordedFailures.isEmpty());
    }

    private final UUID messageId = UUID.randomUUID();

    private PipelineExecutionOutboxMessage message() {
        PipelineExecutionRequest request = new PipelineExecutionRequest(
                messageId, "cash", Map.of("sourceFile", "batch.csv"), now);
        return new PipelineExecutionOutboxMessage(
                messageId, request, now, 0, null);
    }

    @Test
    void shouldKeepFailedMessagePendingAndRecordFailure() {
        FakeOutbox outbox = new FakeOutbox(List.of(message()));
        PipelineExecutionOutboxDispatcher dispatcher =
                new PipelineExecutionOutboxDispatcher(
                        outbox,
                        request -> { throw new IOException("broker unavailable"); },
                        clock);

        OutboxDispatchResult result = dispatcher.dispatchOnce(5);

        assertEquals(new OutboxDispatchResult(1, 0, 1, 0), result);
        assertTrue(outbox.markedPublished.isEmpty());
        assertEquals(1, outbox.recordedFailures.size());
        assertTrue(outbox.recordedFailures.get(0).contains("broker unavailable"));
    }

    @Test
    void shouldStopAfterInterruptedPublishAndRestoreInterruptFlag() {
        FakeOutbox outbox =
                new FakeOutbox(List.of(message(), messageWithNewId()));
        int[] publishCalls = {0};
        PipelineExecutionOutboxDispatcher dispatcher =
                new PipelineExecutionOutboxDispatcher(
                        outbox,
                        request -> {
                            publishCalls[0]++;
                            throw new InterruptedException("interrupted");
                        },
                        clock);

        OutboxDispatchResult result = dispatcher.dispatchOnce(10);

        assertEquals(new OutboxDispatchResult(1, 0, 0, 1), result);
        assertEquals(1, publishCalls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals(1, outbox.recordedFailures.size());
    }

    @Test
    void shouldRejectInvalidBatchSize() {
        FakeOutbox outbox = new FakeOutbox(List.of());
        PipelineExecutionOutboxDispatcher dispatcher =
                new PipelineExecutionOutboxDispatcher(outbox, request -> { }, clock);

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatchOnce(0));
    }

    private PipelineExecutionOutboxMessage messageWithNewId() {
        UUID id = UUID.randomUUID();
        PipelineExecutionRequest request = new PipelineExecutionRequest(
                id, "cash", Map.of("sourceFile", "next.csv"), now);
        return new PipelineExecutionOutboxMessage(id, request, now, 0, null);
    }

    private static final class FakeOutbox implements PipelineExecutionRequestOutbox {
        private final List<PipelineExecutionOutboxMessage> pending;
        private final List<UUID> markedPublished = new ArrayList<>();
        private final List<String> recordedFailures = new ArrayList<>();
        private Instant publishedAt;

        private FakeOutbox(List<PipelineExecutionOutboxMessage> pending) {
            this.pending = pending;
        }

        @Override
        public void enqueue(PipelineExecutionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PipelineExecutionOutboxMessage> findPending(int batchSize) {
            return pending.stream().limit(batchSize).toList();
        }

        @Override
        public boolean markPublished(UUID id, Instant at) {
            markedPublished.add(id);
            publishedAt = at;
            return true;
        }

        @Override
        public boolean recordPublishFailure(UUID id, String error) {
            recordedFailures.add(error);
            return true;
        }
    }
}
