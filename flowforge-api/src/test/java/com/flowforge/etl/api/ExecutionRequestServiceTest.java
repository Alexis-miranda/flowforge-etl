package com.flowforge.etl.api;

import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionRequestServiceTest {

    @Test
    void shouldPersistCashRequestBeforeReturningAcceptedRequest() {
        Instant now = Instant.parse("2026-11-22T10:00:00Z");
        RecordingOutbox outbox = new RecordingOutbox();
        ExecutionRequestService service = new ExecutionRequestService(
                outbox, Clock.fixed(now, ZoneOffset.UTC));

        PipelineExecutionRequest request = service.submit(
                "cash",
                Map.of("input", "/data/input.csv",
                        "backOfficeCode", "BNYM",
                        "outputDirectory", "/data/output"));

        assertNotNull(request.executionId());
        assertEquals("cash", request.pipelineType());
        assertEquals(now, request.requestedAt());
        assertSame(request, outbox.enqueued);
    }

    @Test
    void shouldRejectUnsupportedPipelineType() {
        ExecutionRequestService service = new ExecutionRequestService(
                new RecordingOutbox(), Clock.systemUTC());

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("unknown", Map.of()));
    }

    @Test
    void shouldRejectNullParameterValues() {
        ExecutionRequestService service = new ExecutionRequestService(
                new RecordingOutbox(), Clock.systemUTC());

        assertThrows(IllegalArgumentException.class,
                () -> service.submit("cash", java.util.Collections.singletonMap("input", null)));
    }

    private static final class RecordingOutbox implements PipelineExecutionRequestOutbox {
        private PipelineExecutionRequest enqueued;

        @Override
        public void enqueue(PipelineExecutionRequest request) {
            enqueued = request;
        }

        @Override
        public List<PipelineExecutionOutboxMessage> findPending(int batchSize) {
            return List.of();
        }

        @Override
        public boolean markPublished(UUID messageId, Instant publishedAt) {
            return false;
        }

        @Override
        public boolean recordPublishFailure(UUID messageId, String errorMessage) {
            return false;
        }
    }
}
