package com.flowforge.etl.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small JDK HTTP server exposing the execution submission and query endpoints. */
public final class FlowForgeApiServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
    private final Path uploadDirectory = Path.of(System.getenv().getOrDefault("FLOWFORGE_UPLOAD_DIR", "data/uploads")).toAbsolutePath().normalize();
    private final Path outputDirectory = Path.of(System.getenv().getOrDefault("FLOWFORGE_OUTPUT_DIR", "data/output")).toAbsolutePath().normalize();
    private final String backOfficeCode = System.getenv().getOrDefault("FLOWFORGE_BACK_OFFICE_CODE", "BNYM");

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
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:5173");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-File-Name");
            exchange.getResponseHeaders().set("Vary", "Origin");
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("/api/uploads".equals(path) && "POST".equals(method)) {
                uploadCsv(exchange);
                return;
            }
            if ("/api/executions".equals(path) && "GET".equals(method)) {
                listRecent(exchange);
                return;
            }
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
                exchange.getResponseHeaders().set("Allow", "GET, POST");
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

    private void listRecent(HttpExchange exchange) throws IOException {
        int limit = parseLimit(exchange.getRequestURI().getRawQuery());
        send(exchange, 200, queryService.findRecent(limit));
    }

    private static int parseLimit(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return 25;
        }
        for (String parameter : rawQuery.split("&")) {
            String[] pair = parameter.split("=", 2);
            if ("limit".equals(pair[0])) {
                try {
                    int limit = Integer.parseInt(pair.length == 2 ? pair[1] : "");
                    if (limit < 1 || limit > 100) {
                        throw new IllegalArgumentException("Limit must be between 1 and 100");
                    }
                    return limit;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Limit must be an integer between 1 and 100");
                }
            }
        }
        return 25;
    }

    private void uploadCsv(HttpExchange exchange) throws IOException {
        String rawName = exchange.getRequestHeaders().getFirst("X-File-Name");
        if (rawName == null || rawName.isBlank()) {
            send(exchange, 400, new ApiError("X-File-Name header is required"));
            return;
        }
        String fileName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".csv")
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.equals(".") || fileName.equals("..")) {
            send(exchange, 400, new ApiError("Only CSV files with a valid filename are accepted"));
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_UPLOAD_BYTES + 1);
        if (body.length == 0) {
            send(exchange, 400, new ApiError("Uploaded CSV must not be empty"));
            return;
        }
        if (body.length > MAX_UPLOAD_BYTES) {
            send(exchange, 413, new ApiError("CSV exceeds the 25 MiB upload limit"));
            return;
        }
        Files.createDirectories(uploadDirectory);
        Files.createDirectories(outputDirectory);
        Path storedFile = uploadDirectory.resolve(UUID.randomUUID() + "-" + fileName).normalize();
        if (!storedFile.startsWith(uploadDirectory)) {
            send(exchange, 400, new ApiError("Invalid filename"));
            return;
        }
        Files.write(storedFile, body);
        try {
            PipelineExecutionRequest accepted = requestService.submit("cash", Map.of(
                    "input", storedFile.toString(),
                    "backOfficeCode", backOfficeCode,
                    "outputDirectory", outputDirectory.toString(),
                    "sourceFile", fileName));
            exchange.getResponseHeaders().set("Location", "/api/executions/" + accepted.executionId());
            send(exchange, 202, new SubmissionResponse(
                    accepted.executionId().toString(), "QUEUED",
                    accepted.pipelineType(), accepted.requestedAt()));
        } catch (RuntimeException failure) {
            Files.deleteIfExists(storedFile);
            throw failure;
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
