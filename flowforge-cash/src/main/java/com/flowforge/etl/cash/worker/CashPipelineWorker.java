package com.flowforge.etl.cash.worker;

import com.flowforge.etl.cash.execution.CashPipelineExecutionRequestResolver;
import com.flowforge.etl.cash.execution.PipelineExecutorFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionLeaseRepository;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRepository;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.messaging.consumer.LeaseProtectedPipelineExecutionRequestHandler;
import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestHandler;
import com.flowforge.etl.messaging.rabbitmq.RabbitMqPipelineExecutionRequestConsumer;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Long-running Cash worker that consumes requests from a durable RabbitMQ queue.
 *
 * <p>The worker uses the default RabbitMQ exchange; publishers must therefore
 * publish to exchange "" with the configured queue name as routing key.</p>
 */
public final class CashPipelineWorker implements AutoCloseable {

    private final Connection connection;
    private final Channel channel;
    private final RabbitMqPipelineExecutionRequestConsumer consumer;
    private final ScheduledExecutorService leaseRenewalScheduler;
    private final TrackingHandler trackingHandler;
    private boolean closed;

    private CashPipelineWorker(
            Connection connection,
            Channel channel,
            RabbitMqPipelineExecutionRequestConsumer consumer,
            ScheduledExecutorService leaseRenewalScheduler,
            TrackingHandler trackingHandler) {
        this.connection = connection;
        this.channel = channel;
        this.consumer = consumer;
        this.leaseRenewalScheduler = leaseRenewalScheduler;
        this.trackingHandler = trackingHandler;
    }

    public static CashPipelineWorker createFromEnvironment() throws Exception {
        if (!SqlServerConnectionFactory.isConfigured()) {
            throw new IllegalStateException(
                    "SQL Server must be configured with FLOWFORGE_DB_JDBC_URL, "
                            + "FLOWFORGE_DB_USER and FLOWFORGE_DB_PASSWORD");
        }

        String rabbitUri = requiredEnvironmentVariable("FLOWFORGE_RABBITMQ_URI");
        String queue = environmentOrDefault(
                "FLOWFORGE_RABBITMQ_QUEUE", "flowforge.pipeline.requests");
        String workerId = environmentOrDefault(
                "FLOWFORGE_WORKER_ID", "cash-worker-" + UUID.randomUUID());
        int prefetch = positiveInt("FLOWFORGE_WORKER_PREFETCH", 1);
        long leaseSeconds = positiveLong("FLOWFORGE_WORKER_LEASE_SECONDS", 90);
        long renewalSeconds = positiveLong("FLOWFORGE_WORKER_LEASE_RENEWAL_SECONDS", 20);
        if (renewalSeconds >= leaseSeconds) {
            throw new IllegalArgumentException(
                    "Lease renewal interval must be shorter than lease duration");
        }

        Connection connection = null;
        Channel channel = null;
        ScheduledExecutorService scheduler = null;
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(rabbitUri);
            connectionFactory.setRequestedHeartbeat(30);
            connection = connectionFactory.newConnection(workerId);
            channel = connection.createChannel();

            Map<String, Object> queueArguments = new HashMap<>();
            String deadLetterExchange = System.getenv(
                    "FLOWFORGE_RABBITMQ_DEAD_LETTER_EXCHANGE");
            if (deadLetterExchange != null && !deadLetterExchange.isBlank()) {
                queueArguments.put("x-dead-letter-exchange", deadLetterExchange.trim());
            }
            channel.queueDeclare(queue, true, false, false, queueArguments);

            PipelineExecutionRepository executionRepository =
                    new SqlServerPipelineExecutionRepository();
            PipelineExecutionLeaseRepository leaseRepository =
                    new SqlServerPipelineExecutionLeaseRepository();

            var executor = PipelineExecutorFactory.create(null, executionRepository);
            var resolver = new CashPipelineExecutionRequestResolver(executor);
            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "flowforge-lease-renewal");
                thread.setDaemon(true);
                return thread;
            });

            var handler = new LeaseProtectedPipelineExecutionRequestHandler(
                    resolver,
                    leaseRepository,
                    executionRepository,
                    workerId,
                    Duration.ofSeconds(leaseSeconds),
                    Duration.ofSeconds(renewalSeconds),
                    scheduler);
            TrackingHandler trackingHandler = new TrackingHandler(handler);
            var consumer = new RabbitMqPipelineExecutionRequestConsumer(
                    channel,
                    new PipelineExecutionRequestJsonCodec(),
                    trackingHandler,
                    queue,
                    prefetch);

            return new CashPipelineWorker(
                    connection, channel, consumer, scheduler, trackingHandler);
        } catch (Exception startupFailure) {
            closeQuietly(channel);
            closeQuietly(connection);
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            throw startupFailure;
        }
    }

    public void start() throws IOException {
        consumer.start();
    }

    /**
     * Cancels new deliveries, waits for in-flight handlers, then stops lease
     * renewal and closes the broker resources.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            consumer.close();
        } catch (IOException exception) {
            System.err.println("Could not cancel RabbitMQ consumer: " + exception.getMessage());
        }

        trackingHandler.awaitIdle();

        leaseRenewalScheduler.shutdown();
        try {
            if (!leaseRenewalScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                leaseRenewalScheduler.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            leaseRenewalScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        closeQuietly(channel);
        closeQuietly(connection);
    }

    public static void main(String[] args) throws Exception {
        CashPipelineWorker worker = createFromEnvironment();
        Runtime.getRuntime().addShutdownHook(
                new Thread(worker::close, "flowforge-worker-shutdown"));
        worker.start();
        System.out.println("FlowForge Cash worker is consuming requests. "
                + "Press Ctrl+C to stop.");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            worker.close();
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name);
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
            System.err.println("Could not close worker resource: " + exception.getMessage());
        }
    }

    private static final class TrackingHandler implements PipelineExecutionRequestHandler {
        private final PipelineExecutionRequestHandler delegate;
        private int activeRequests;

        private TrackingHandler(PipelineExecutionRequestHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(PipelineExecutionRequest request) throws Exception {
            synchronized (this) {
                activeRequests++;
            }
            try {
                delegate.handle(request);
            } finally {
                synchronized (this) {
                    activeRequests--;
                    notifyAll();
                }
            }
        }

        private synchronized void awaitIdle() {
            boolean interrupted = false;
            while (activeRequests > 0) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
