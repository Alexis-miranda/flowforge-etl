package com.flowforge.etl.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestStatus;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlowForgeApiServerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldAcceptRequestAndExposeQueuedStatusAndSteps() throws Exception {
        RecordingOutbox outbox = new RecordingOutbox();
        var requestService = new ExecutionRequestService(outbox, Clock.systemUTC());
        var queryService = new ExecutionQueryService(new EmptyExecutionRepository(), outbox);

        try (FlowForgeApiServer server =
                     new FlowForgeApiServer("127.0.0.1", 0, requestService, queryService)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + server.port());

            HttpRequest submit = HttpRequest.newBuilder(base.resolve("/api/executions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {
                              "pipelineType": "cash",
                              "parameters": {
                                "input": "/shared/input.csv",
                                "backOfficeCode": "BNYM",
                                "outputDirectory": "/shared/output"
                              }
                            }
                            """))
                    .build();

            HttpResponse<String> accepted =
                    client.send(submit, HttpResponse.BodyHandlers.ofString());

            assertEquals(202, accepted.statusCode());
            JsonNode response = mapper.readTree(accepted.body());
            String executionId = response.get("executionId").asText();
            assertEquals("QUEUED", response.get("status").asText());
            assertNotNull(outbox.enqueued);

            HttpResponse<String> recent = client.send(
                    HttpRequest.newBuilder(base.resolve("/api/executions?limit=10")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, recent.statusCode());
            JsonNode recentResponse = mapper.readTree(recent.body());
            assertEquals(1, recentResponse.size());
            assertEquals(executionId, recentResponse.get(0).get("executionId").asText());
            assertEquals("QUEUED", recentResponse.get(0).get("status").asText());

            HttpResponse<String> status = client.send(
                    HttpRequest.newBuilder(base.resolve(
                            "/api/executions/" + executionId)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, status.statusCode());
            assertEquals("QUEUED", mapper.readTree(status.body()).get("status").asText());

            HttpResponse<String> steps = client.send(
                    HttpRequest.newBuilder(base.resolve(
                            "/api/executions/" + executionId + "/steps")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, steps.statusCode());
            JsonNode stepsResponse = mapper.readTree(steps.body());
            assertEquals("QUEUED", stepsResponse.get("status").asText());
            assertEquals(0, stepsResponse.get("steps").size());
        }
    }

    @Test
    void shouldReturnEmptyHistoryAndUseDefaultLimitWhenNoRequestsExist() throws Exception {
        RecordingOutbox outbox = new RecordingOutbox();
        var requestService = new ExecutionRequestService(outbox, Clock.systemUTC());
        var queryService = new ExecutionQueryService(new EmptyExecutionRepository(), outbox);

        try (FlowForgeApiServer server =
                     new FlowForgeApiServer("127.0.0.1", 0, requestService, queryService)) {
            server.start();
            URI endpoint = URI.create("http://127.0.0.1:" + server.port() + "/api/executions");

            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(endpoint).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(0, mapper.readTree(response.body()).size());
            assertEquals(25, outbox.lastRecentLimit);
        }
    }

    @Test
    void shouldRejectInvalidRecentExecutionLimits() throws Exception {
        RecordingOutbox outbox = new RecordingOutbox();
        var requestService = new ExecutionRequestService(outbox, Clock.systemUTC());
        var queryService = new ExecutionQueryService(new EmptyExecutionRepository(), outbox);

        try (FlowForgeApiServer server =
                     new FlowForgeApiServer("127.0.0.1", 0, requestService, queryService)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + server.port());

            for (String invalidLimit : List.of("0", "101", "abc", "")) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(base.resolve(
                                "/api/executions?limit=" + invalidLimit)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(400, response.statusCode(), "limit=" + invalidLimit);
                assertTrue(mapper.readTree(response.body()).has("error"));
            }

            assertEquals(-1, outbox.lastRecentLimit,
                    "Invalid limits must be rejected before querying the outbox");
        }
    }

    @Test
    void shouldAcceptMinimumAndMaximumRecentExecutionLimits() throws Exception {
        RecordingOutbox outbox = new RecordingOutbox();
        var requestService = new ExecutionRequestService(outbox, Clock.systemUTC());
        var queryService = new ExecutionQueryService(new EmptyExecutionRepository(), outbox);

        try (FlowForgeApiServer server =
                     new FlowForgeApiServer("127.0.0.1", 0, requestService, queryService)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + server.port());

            for (int limit : List.of(1, 100)) {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(base.resolve(
                                "/api/executions?limit=" + limit)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode());
                assertEquals(limit, outbox.lastRecentLimit);
            }
        }
    }

    private static final class RecordingOutbox implements PipelineExecutionRequestOutbox {
        private PipelineExecutionRequest enqueued;
        private int lastRecentLimit = -1;

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

        @Override
        public List<PipelineExecutionRequestStatus> findRecentRequests(int limit) {
            lastRecentLimit = limit;
            if (enqueued == null) {
                return List.of();
            }
            return List.of(new PipelineExecutionRequestStatus(
                    enqueued.executionId(), enqueued.pipelineType(), "PENDING",
                    enqueued.requestedAt(), 0, null));
        }

        @Override
        public Optional<PipelineExecutionRequestStatus> findRequestStatus(UUID executionId) {
            if (enqueued == null || !enqueued.executionId().equals(executionId)) {
                return Optional.empty();
            }
            return Optional.of(new PipelineExecutionRequestStatus(
                    executionId, enqueued.pipelineType(), "PENDING",
                    enqueued.requestedAt(), 0, null));
        }
    }

    private static final class EmptyExecutionRepository implements PipelineExecutionRepository {
        @Override
        public void start(PipelineExecutionSnapshot snapshot) { }

        @Override
        public void update(PipelineExecutionSnapshot snapshot) { }

        @Override
        public void save(PipelineExecutionResult result) { }

        @Override
        public Optional<PipelineExecutionSnapshot> findSnapshotByExecutionId(String executionId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecutionResult> findByPipelineId(String pipelineId) {
            return List.of();
        }

        @Override
        public Optional<PipelineExecutionResult> findByExecutionId(String executionId) {
            return Optional.empty();
        }

        @Override
        public List<PipelineExecutionSnapshot> findSnapshotsByPipelineId(String pipelineId) {
            return List.of();
        }
    }
}
