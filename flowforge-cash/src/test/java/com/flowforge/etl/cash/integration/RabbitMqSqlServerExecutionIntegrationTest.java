package com.flowforge.etl.cash.integration;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionLeaseRepository;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRepository;
import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;
import com.flowforge.etl.messaging.consumer.LeaseProtectedPipelineExecutionRequestHandler;
import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestPipeline;
import com.flowforge.etl.messaging.rabbitmq.RabbitMqPipelineExecutionRequestConsumer;
import com.flowforge.etl.messaging.rabbitmq.RabbitMqPipelineExecutionRequestPublisher;
import com.flowforge.etl.messaging.rabbitmq.RabbitMqRetryPolicy;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import com.flowforge.etl.messaging.serialization.PipelineExecutionRequestJsonCodec;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqSqlServerExecutionIntegrationTest {

    private static final String RABBIT_URI_ENV = "FLOWFORGE_TEST_RABBITMQ_URI";

    @Test
    void shouldPublishConsumeExecuteAndPersistPipelineInSqlServer() throws Exception {
        requireIntegrationServices();

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String queue = "flowforge-it-" + suffix;
        String retryQueue = queue + ".retry";
        String deadQueue = queue + ".dead";
        String executionId = UUID.randomUUID().toString();
        String pipelineId = "rabbitmq-sqlserver-it-" + suffix;

        try (Broker broker = Broker.connect()) {
            broker.declareTopology(queue, retryQueue, deadQueue, 100);
            SqlServerPipelineExecutionRepository executions =
                    new SqlServerPipelineExecutionRepository();
            SqlServerPipelineExecutionLeaseRepository leases =
                    new SqlServerPipelineExecutionLeaseRepository();

            PipelineDefinition definition = new PipelineDefinition(
                    pipelineId, "RabbitMQ SQL integration", "One deterministic integration step",
                    List.of(new PipelineStep("integration-step", "Integration step",
                            StepType.TRANSFORM, 1)));
            StepExecutor stepExecutor = new StepExecutor() {
                @Override
                public StepType supportedType() {
                    return StepType.TRANSFORM;
                }

                @Override
                public void execute(PipelineStep step,
                                    com.flowforge.etl.core.execution.PipelineExecutionContext context) {
                    // The purpose of this step is to exercise the real worker-to-SQL lifecycle.
                }
            };
            PipelineExecutor executor = new PipelineExecutor(
                    List.of(stepExecutor), null, executions);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            try {
                var handler = new LeaseProtectedPipelineExecutionRequestHandler(
                        request -> new PipelineExecutionRequestPipeline(definition, executor),
                        leases, executions, "integration-worker-" + suffix,
                        Duration.ofSeconds(30), Duration.ofSeconds(5), scheduler);
                broker.startConsumer(queue, handler, retryQueue, deadQueue);
                PipelineExecutionRequest request = new PipelineExecutionRequest(
                        UUID.fromString(executionId), "integration",
                        Map.of("case", "successful-execution"), Instant.now());
                broker.publish(queue, request);

                awaitCondition(() -> executions.findSnapshotByExecutionId(executionId)
                                .map(snapshot -> snapshot.status().name().equals("SUCCEEDED"))
                                .orElse(false),
                        Duration.ofSeconds(15),
                        "Pipeline execution did not reach SUCCEEDED in SQL Server");

                assertEquals("SUCCEEDED",
                        executions.findSnapshotByExecutionId(executionId).orElseThrow()
                                .status().name());
                assertEquals(1, executions.findStepExecutions(executionId).size());
                assertEquals("SUCCEEDED",
                        executions.findStepExecutions(executionId).get(0).status().name());
            } finally {
                scheduler.shutdownNow();
            }
        }
    }

    @Test
    void shouldMoveRepeatedTransientFailuresToDeadLetterQueue() throws Exception {
        requireIntegrationServices();

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String queue = "flowforge-retry-it-" + suffix;
        String retryQueue = queue + ".retry";
        String deadQueue = queue + ".dead";

        try (Broker broker = Broker.connect()) {
            broker.declareTopology(queue, retryQueue, deadQueue, 100);
            broker.startConsumer(queue,
                    request -> { throw new IllegalStateException("simulated transient failure"); },
                    retryQueue, deadQueue);

            PipelineExecutionRequest request = new PipelineExecutionRequest(
                    UUID.randomUUID(), "integration", Map.of("case", "retry-to-dlq"), Instant.now());
            broker.publish(queue, request);

            awaitCondition(() -> broker.messageCount(deadQueue) == 1,
                    Duration.ofSeconds(15),
                    "Message did not reach the dead-letter queue after bounded retries");

            var deadLetter = broker.getOne(deadQueue);
            assertNotNull(deadLetter, "Expected one message in the dead-letter queue");
            assertEquals(request.executionId().toString(), deadLetter.getProps().getMessageId());
            assertEquals(1,
                    ((Number) deadLetter.getProps().getHeaders().get("x-flowforge-retry-count")).intValue());
            broker.acknowledge(deadLetter.getEnvelope().getDeliveryTag());
        }
    }

    private static void requireIntegrationServices() {
        Assumptions.assumeTrue(SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration requires FLOWFORGE_DB_* environment variables");
        Assumptions.assumeTrue(System.getenv(RABBIT_URI_ENV) != null
                        && !System.getenv(RABBIT_URI_ENV).isBlank(),
                "RabbitMQ integration requires FLOWFORGE_TEST_RABBITMQ_URI");
    }

    private static void awaitCondition(BooleanSupplier condition, Duration timeout,
                                       String failureMessage) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail(failureMessage);
    }

    private static final class Broker implements AutoCloseable {
        private final Connection connection;
        private final Channel publisherChannel;
        private final Channel consumerChannel;
        private final PipelineExecutionRequestJsonCodec codec =
                new PipelineExecutionRequestJsonCodec();

        private Broker(Connection connection, Channel publisherChannel, Channel consumerChannel) {
            this.connection = connection;
            this.publisherChannel = publisherChannel;
            this.consumerChannel = consumerChannel;
        }

        static Broker connect() throws Exception {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUri(System.getenv(RABBIT_URI_ENV));
            factory.setConnectionTimeout(5000);
            Connection connection = factory.newConnection("flowforge-integration-test");
            return new Broker(connection, connection.createChannel(), connection.createChannel());
        }

        void declareTopology(String queue, String retryQueue, String deadQueue, long retryDelay)
                throws Exception {
            consumerChannel.confirmSelect();
            consumerChannel.queueDeclare(queue, false, false, true, Map.of());
            consumerChannel.queueDeclare(retryQueue, false, false, true, Map.of(
                    "x-message-ttl", retryDelay,
                    "x-dead-letter-exchange", "",
                    "x-dead-letter-routing-key", queue));
            consumerChannel.queueDeclare(deadQueue, false, false, true, Map.of());
        }

        void startConsumer(String queue,
                           com.flowforge.etl.messaging.consumer.PipelineExecutionRequestHandler handler,
                           String retryQueue, String deadQueue) throws Exception {
            var consumer = new RabbitMqPipelineExecutionRequestConsumer(
                    consumerChannel, codec, handler, queue, 1,
                    new RabbitMqRetryPolicy(retryQueue, deadQueue, 1, 100, 5000));
            consumer.start();
        }

        void publish(String queue, PipelineExecutionRequest request) throws Exception {
            var publisher = new RabbitMqPipelineExecutionRequestPublisher(
                    publisherChannel, codec, "", queue, 5000);
            publisher.publish(request);
        }

        long messageCount(String queue) throws Exception {
            return consumerChannel.queueDeclarePassive(queue).getMessageCount();
        }

        com.rabbitmq.client.GetResponse getOne(String queue) throws Exception {
            return consumerChannel.basicGet(queue, false);
        }

        void acknowledge(long deliveryTag) throws Exception {
            consumerChannel.basicAck(deliveryTag, false);
        }

        @Override
        public void close() throws Exception {
            if (consumerChannel.isOpen()) consumerChannel.close();
            if (publisherChannel.isOpen()) publisherChannel.close();
            if (connection.isOpen()) connection.close();
        }
    }
}
