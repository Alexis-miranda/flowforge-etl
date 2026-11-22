package com.flowforge.etl.connectors.database.sqlserver;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.PipelineExecutionSnapshot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SqlServerPipelineExecutionLeaseRepositoryIntegrationTest {

    @Test
    void shouldAcquireRenewReleaseAndReacquireWithHigherFencingToken() {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionLeaseRepository repository =
                new SqlServerPipelineExecutionLeaseRepository();
        String executionId = UUID.randomUUID().toString();
        createRunningAuditRecord(executionId);
        Duration duration = Duration.ofSeconds(30);

        PipelineExecutionLease first = repository.tryAcquire(
                executionId, "worker-1", duration).orElseThrow();
        assertEquals(1, first.fencingToken());
        assertTrue(repository.tryAcquire(executionId, "worker-2", duration).isEmpty());

        assertTrue(repository.renew(first, duration));
        assertTrue(repository.release(first));
        assertFalse(repository.release(first));

        PipelineExecutionLease second = repository.tryAcquire(
                executionId, "worker-2", duration).orElseThrow();
        assertEquals(first.fencingToken() + 1, second.fencingToken());
        assertEquals("worker-2", second.ownerId());
    }

    @Test
    void shouldReclaimExpiredLeaseWithHigherFencingToken() throws Exception {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionLeaseRepository repository =
                new SqlServerPipelineExecutionLeaseRepository();
        String executionId = UUID.randomUUID().toString();
        createRunningAuditRecord(executionId);
        PipelineExecutionLease first = repository.tryAcquire(
                executionId, "worker-1", Duration.ofSeconds(1)).orElseThrow();

        Thread.sleep(1_200);

        PipelineExecutionLease second = repository.tryAcquire(
                executionId, "worker-2", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(first.fencingToken() + 1, second.fencingToken());
    }

    private static void createRunningAuditRecord(String executionId) {
        Instant startedAt = Instant.now();\n        new SqlServerPipelineExecutionRepository().start(\n                new PipelineExecutionSnapshot(\n                        executionId,\n                        "lease-integration-" + executionId,\n                        PipelineStatus.RUNNING,\n                        null,\n                        null,\n                        startedAt,\n                        null));\n    }\n\n    @Test\n    void shouldAllowOnlyOneConcurrentLeaseAcquisition() throws Exception {
        Assumptions.assumeTrue(
                SqlServerConnectionFactory.isConfigured(),
                "SQL Server integration test requires database environment variables");

        SqlServerPipelineExecutionLeaseRepository repository =
                new SqlServerPipelineExecutionLeaseRepository();
        String executionId = UUID.randomUUID().toString();
        createRunningAuditRecord(executionId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<PipelineExecutionLease>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.tryAcquire(executionId, "worker-1",
                        Duration.ofSeconds(30));
            });
            Future<Optional<PipelineExecutionLease>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.tryAcquire(executionId, "worker-2",
                        Duration.ofSeconds(30));
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(15, TimeUnit.SECONDS).isPresent(),
                    second.get(15, TimeUnit.SECONDS).isPresent());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
