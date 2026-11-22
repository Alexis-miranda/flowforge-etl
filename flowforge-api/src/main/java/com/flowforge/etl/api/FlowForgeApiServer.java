package com.flowforge.etl.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small JDK HTTP server exposing the execution submission and query endpoints. */
public final class FlowForgeApiServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 1_048_576;

    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final ObjectMapper objectMapper;
    private final ExecutionRequestService requestService;
    private final ExecutionQueryService queryService;

    public FlowForgeApiServer(
            String host,
            int port,
            ExecutionRequestService requestService,
            ExecutionQueryService queryService) throws IOException {
        this.requestService = requestService;
        this.queryService = queryService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.httpExecutor = Executors.newFixedThreadPool(4, task -> {
            Thread thread = new Thread(task, "flowforge-api-http");
            thread.setDaemon(false);
            return thread;
        });
        this.server.setExecutor(httpExecutor);
        this.server.createContext("/api/executions", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("/api/executions".equals(path) && "POST".equals(method)) {
                submit(exchange);
                return;
            }

            String[] segments = path.split("/");
            if (segments.length == 4
                    && "api".equals(segments[1])
                    && "executions".equals(segments[2])
                    && "GET".equals(method)) {
                getStatus(exchange, segments[3]);
                return;
            }
            if (segments.length == 5
                    && "api".equals(segments[1])
                    && "executions".equals(segments[2])
                    && "steps".equals(segments[4])
                    && "GET".equals(method)) {
                getSteps(exchange, segments[3]);
                return;
            }

            if ("/api/executions".equals(path)
                    || path.startsWith("/api/executions/")) {
                exchange.getResponseHeaders().set("Allow", "/api/executions: POST; /api/executions/{id}: GET");
                send(exchange, 405, new ApiError("Method or endpoint not supported"));
            } else {
                send(exchange, 404, new ApiError("Endpoint not found"));
            }
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, new ApiError(exception.getMessage()));
        } catch (IllegalStateException exception) {
            send(exchange, 503, new ApiError("Execution service is temporarily unavailable"));
        } catch (Exception exception) {
            send(exchange, 500, new ApiError("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    private void submit(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            send(exchange, 413, new ApiError("Request body exceeds 1 MiB"));
            return;
        }

        CreateExecutionRequest payload;
        try {
            payload = objectMapper.readValue(body, CreateExecutionRequest.class);
        } catch (Exception exception) {
            send(exchange, 400, new ApiError("Request body must be valid JSON"));
            return;
        }

        if (payload == null) {
            send(exchange, 400, new ApiError("Request body must not be empty"));
            return;
        }

        PipelineExecutionRequest accepted =
                requestService.submit(payload.pipelineType(), payload.parameters());
        exchange.getResponseHeaders().set(
                "Location", "/api/executions/" + accepted.executionId());
        send(exchange, 202, new SubmissionResponse(
                accepted.executionId().toString(),
                "QUEUED",
                accepted.pipelineType(),
                accepted.requestedAt()));
    }

    private void getStatus(HttpExchange exchange, String rawId) throws IOException {
        UUID executionId = parseExecutionId(rawId);
        var status = queryService.findStatus(executionId);
        if (status.isEmpty()) {
            send(exchange, 404, new ApiError("Execution request not found"));
            return;
        }
        send(exchange, 200, status.get());
    }

    private void getSteps(HttpExchange exchange, String rawId) throws IOException {
        UUID executionId = parseExecutionId(rawId);
        var steps = queryService.findSteps(executionId);
        if (steps.isEmpty()) {
            send(exchange, 404, new ApiError("Execution request not found"));
            return;
        }
        send(exchange, 200, steps.get());
    }

    private static UUID parseExecutionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Execution ID must be a valid UUID");
        }
    }

    private void send(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @Override
    public void close() {
        server.stop(1);
        httpExecutor.shutdown();
    }

    public record CreateExecutionRequest(
            String pipelineType,
            Map<String, String> parameters) { }

    public record SubmissionResponse(
            String executionId,
            String status,
            String pipelineType,
            java.time.Instant requestedAt) { }

    public record ApiError(String error) { }
}
