package com.flowforge.etl.api;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRepository;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.dispatch.PipelineExecutionOutboxDispatcher;
import com.flowforge.etl.messaging.rabbitmq.RabbitMqPipelineExecutionRequestPublisher;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runnable HTTP application that persists requests and dispatches its outbox to RabbitMQ. */
public final class FlowForgeApiApplication implements AutoCloseable {

    private final Connection connection;
    private final Channel channel;
    private final ScheduledExecutorService dispatcherScheduler;
    private final FlowForgeApiServer server;

    private FlowForgeApiApplication(
            Connection connection,
            Channel channel,
            ScheduledExecutorService dispatcherScheduler,
            FlowForgeApiServer server) {
        this.connection = connection;
        this.channel = channel;
        this.dispatcherScheduler = dispatcherScheduler;
        this.server = server;
    }

    public static FlowForgeApiApplication createFromEnvironment() throws Exception {
        if (!SqlServerConnectionFactory.isConfigured()) {
            throw new IllegalStateException(
                    "SQL Server must be configured with FLOWFORGE_DB_JDBC_URL, "
                            + "FLOWFORGE_DB_USER and FLOWFORGE_DB_PASSWORD");
        }

        String rabbitUri = requiredEnvironmentVariable("FLOWFORGE_RABBITMQ_URI");
        String queue = environmentOrDefault(
                "FLOWFORGE_RABBITMQ_QUEUE", "flowforge.pipeline.requests");
        String host = environmentOrDefault("FLOWFORGE_API_HOST", "127.0.0.1");
        int port = positiveInt("FLOWFORGE_API_PORT", 8080);
        int batchSize = positiveInt("FLOWFORGE_API_DISPATCH_BATCH_SIZE", 50);
        long dispatchIntervalMillis =
                positiveLong("FLOWFORGE_API_DISPATCH_INTERVAL_MILLIS", 1000);
        long confirmTimeoutMillis =
                positiveLong("FLOWFORGE_API_PUBLISH_CONFIRM_TIMEOUT_MILLIS", 5000);

        Connection connection = null;
        Channel channel = null;
        ScheduledExecutorService scheduler = null;
        FlowForgeApiServer server = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(rabbitUri);
            factory.setRequestedHeartbeat(30);
            connection = factory.newConnection("flowforge-api");
            channel = connection.createChannel();

            Map<String, Object> queueArguments = new HashMap<>();
            String deadLetterExchange =
                    System.getenv("FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE");
            if (deadLetterExchange != null && !deadLetterExchange.isBlank()) {
                queueArguments.put("x-dead-letter-exchange", deadLetterExchange.trim());
            }
            channel.queueDeclare(queue, true, false, false, queueArguments);

            PipelineExecutionRequestJsonCodec codec =
                    new PipelineExecutionRequestJsonCodec();
            var publisher = new RabbitMqPipelineExecutionRequestPublisher(
                    channel, codec, "", queue, confirmTimeoutMillis);
            var outbox = new SqlServerPipelineExecutionRequestOutbox(codec);
            var dispatcher = new PipelineExecutionOutboxDispatcher(
                    outbox, publisher, java.time.Clock.systemUTC());

            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "flowforge-outbox-dispatcher");
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    dispatcher.dispatchOnce(batchSize);
                } catch (RuntimeException failure) {
                    System.err.println("Outbox dispatch pass failed: "
                            + failure.getMessage());
                }
            }, 0, dispatchIntervalMillis, TimeUnit.MILLISECONDS);

            var executions = new SqlServerPipelineExecutionRepository();
            var requestService = new ExecutionRequestService(
                    outbox, java.time.Clock.systemUTC());
            var queryService = new ExecutionQueryService(executions, outbox);
            server = new FlowForgeApiServer(
                    host, port, requestService, queryService);

            return new FlowForgeApiApplication(connection, channel, scheduler, server);
        } catch (Exception startupFailure) {
            if (server != null) {
                server.close();
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            closeQuietly(channel);
            closeQuietly(connection);
            throw startupFailure;
        }
    }

    public void start() {
        server.start();
        System.out.println("FlowForge REST API listening on port " + server.port());
    }

    @Override
    public void close() {
        dispatcherScheduler.shutdown();
        try {
            if (!dispatcherScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                dispatcherScheduler.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            dispatcherScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        server.close();
        closeQuietly(channel);
        closeQuietly(connection);
    }

    public static void main(String[] args) throws Exception {
        FlowForgeApiApplication application = createFromEnvironment();
        Runtime.getRuntime().addShutdownHook(
                new Thread(application::close, "flowforge-api-shutdown"));
        application.start();
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            application.close();
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int positiveInt(String name, int defaultValue) {
        long value = positiveLong(name, defaultValue);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " is too large");
        }
        return (int) value;
    }

    private static long positiveLong(String name, long defaultValue) {
        String configured = System.getenv(name);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(configured.trim());
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception exception) {
            System.err.println("Could not close API resource: " + exception.getMessage());
        }
    }
}
