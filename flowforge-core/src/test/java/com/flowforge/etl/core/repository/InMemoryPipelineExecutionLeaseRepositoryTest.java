package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPipelineExecutionLeaseRepositoryTest {

    private static final Instant NOW =
            Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    @Test
    void shouldAllowOnlyOneWorkerToAcquireLease() throws Exception {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<PipelineExecutionLease>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.tryAcquire("execution-1", "worker-1",
                        NOW, LEASE_DURATION);
            });
            Future<Optional<PipelineExecutionLease>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return repository.tryAcquire("execution-1", "worker-2",
                        NOW, LEASE_DURATION);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS).isPresent(),
                    second.get(5, TimeUnit.SECONDS).isPresent());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowReacquisitionAfterExpiryWithHigherFencingToken() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();

        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", NOW, LEASE_DURATION).orElseThrow();

        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", NOW.plusSeconds(30),
                LEASE_DURATION).orElseThrow();

        assertEquals(first.fencingToken() + 1, second.fencingToken());
        assertEquals("worker-2", second.ownerId());
    }

    @Test
    void shouldNotRenewExpiredOrSupersededLease() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();
        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", NOW, LEASE_DURATION).orElseThrow();

        assertFalse(repository.renew(first, NOW.plusSeconds(30),
                LEASE_DURATION));

        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", NOW.plusSeconds(30),
                LEASE_DURATION).orElseThrow();

        assertFalse(repository.renew(first, NOW.plusSeconds(31),
                LEASE_DURATION));
        assertTrue(repository.renew(second, NOW.plusSeconds(31),
                LEASE_DURATION));
    }

    @Test
    void shouldRetainFencingTokenAfterRelease() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();
        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", NOW, LEASE_DURATION).orElseThrow();

        assertTrue(repository.release(first, NOW.plusSeconds(1)));
        assertFalse(repository.release(first, NOW.plusSeconds(2)));

        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", NOW.plusSeconds(2),
                LEASE_DURATION).orElseThrow();

        assertEquals(first.fencingToken() + 1, second.fencingToken());
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repository.tryAcquire("execution-1", "worker-1",
                        NOW, Duration.ZERO));
    }
}
