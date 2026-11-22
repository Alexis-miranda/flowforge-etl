package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlServerPipelineExecutionRequestOutboxIntegrationTest {

    private final PipelineExecutionRequestJsonCodec codec =
            new PipelineExecutionRequestJsonCodec();

    @Test
    void shouldAtomicallyEnqueueIdempotentlyAndTrackOutboxDelivery() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionRequestOutbox outbox =
                new SqlServerPipelineExecutionRequestOutbox(codec);
        PipelineExecutionRequest request = new PipelineExecutionRequest(
                UUID.randomUUID(),
                "cash",
                Map.of("sourceFile", "batch-001.csv", "outputDir", "/tmp/out"),
                Instant.now());

        outbox.enqueue(request);
        // A retry with the same execution ID and value must not create a second row.
        outbox.enqueue(request);

        List<PipelineExecutionOutboxMessage> pending =
                outbox.findPending(100);
        List<PipelineExecutionOutboxMessage> matching = pending.stream()
                .filter(message -> message.messageId().equals(request.executionId()))
                .toList();

        assertEquals(1, matching.size());
        assertEquals(request, matching.get(0).request());
        assertEquals(0, matching.get(0).publishAttempts());
        assertTrue(outbox.recordPublishFailure(
                request.executionId(), "Temporary broker failure"));

        PipelineExecutionOutboxMessage failedAttempt = outbox.findPending(100).stream()
                .filter(message -> message.messageId().equals(request.executionId()))
                .findFirst().orElseThrow();
        assertEquals(1, failedAttempt.publishAttempts());
        assertEquals("Temporary broker failure", failedAttempt.lastError());

        Instant publishedAt = Instant.now();
        assertTrue(outbox.markPublished(request.executionId(), publishedAt));
        assertFalse(outbox.markPublished(request.executionId(), publishedAt));
        assertFalse(outbox.findPending(100).stream()
                .anyMatch(message -> message.messageId().equals(request.executionId())));
    }

    @Test
    void shouldRejectReusingExecutionIdForDifferentRequestContent() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionRequestOutbox outbox =
                new SqlServerPipelineExecutionRequestOutbox(codec);
        UUID executionId = UUID.randomUUID();
        outbox.enqueue(new PipelineExecutionRequest(
                executionId, "cash", Map.of("sourceFile", "first.csv"), Instant.now()));

        PipelineExecutionRequest conflicting = new PipelineExecutionRequest(
                executionId, "cash", Map.of("sourceFile", "different.csv"), Instant.now());

        assertThrows(IllegalStateException.class, () -> outbox.enqueue(conflicting));
    }

    @Test
    void shouldRejectInvalidBatchSize() {
        SqlServerPipelineExecutionRequestOutbox outbox =
                new SqlServerPipelineExecutionRequestOutbox(codec);
        assertThrows(IllegalArgumentException.class, () -> outbox.findPending(0));
    }
}
