package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPipelineExecutionLeaseRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    @Test
    void shouldAllowOnlyOneWorkerToAcquireLease() throws Exception {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository(Clock.fixed(NOW, ZoneId.of("UTC")));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<PipelineExecutionLease>> first = executor.submit(() -> {
                ready.countDown(); start.await();
                return repository.tryAcquire("execution-1", "worker-1", LEASE_DURATION);
            });
            Future<Optional<PipelineExecutionLease>> second = executor.submit(() -> {
                ready.countDown(); start.await();
                return repository.tryAcquire("execution-1", "worker-2", LEASE_DURATION);
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
        MutableClock clock = new MutableClock(NOW);
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository(clock);
        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", LEASE_DURATION).orElseThrow();

        clock.advance(Duration.ofSeconds(30));
        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", LEASE_DURATION).orElseThrow();

        assertEquals(first.fencingToken() + 1, second.fencingToken());
        assertEquals("worker-2", second.ownerId());
    }

    @Test
    void shouldNotRenewExpiredOrSupersededLease() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository(clock);
        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", LEASE_DURATION).orElseThrow();

        clock.advance(Duration.ofSeconds(30));
        assertFalse(repository.renew(first, LEASE_DURATION));

        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", LEASE_DURATION).orElseThrow();
        assertFalse(repository.renew(first, LEASE_DURATION));
        assertTrue(repository.renew(second, LEASE_DURATION));
    }

    @Test
    void shouldRetainFencingTokenAfterRelease() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository(Clock.fixed(NOW, ZoneId.of("UTC")));
        PipelineExecutionLease first = repository.tryAcquire(
                "execution-1", "worker-1", LEASE_DURATION).orElseThrow();

        assertTrue(repository.release(first));
        assertFalse(repository.release(first));
        PipelineExecutionLease second = repository.tryAcquire(
                "execution-1", "worker-2", LEASE_DURATION).orElseThrow();
        assertEquals(first.fencingToken() + 1, second.fencingToken());
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {
        InMemoryPipelineExecutionLeaseRepository repository =
                new InMemoryPipelineExecutionLeaseRepository();
        assertThrows(IllegalArgumentException.class,
                () -> repository.tryAcquire("execution-1", "worker-1", Duration.ZERO));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private MutableClock(Instant initial) { current = new AtomicReference<>(initial); }
        void advance(Duration duration) { current.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
