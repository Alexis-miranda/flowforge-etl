package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.PipelineExecutionLeaseLostException;
import com.flowforge.etl.core.repository.PipelineExecutionLeaseRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RenewingPipelineExecutionLeaseGuardTest {

    @Test
    void shouldFailClosedWhenRepositoryRejectsRenewal() throws Exception {
        AtomicInteger renewals = new AtomicInteger();
        CountDownLatch attempted = new CountDownLatch(1);
        PipelineExecutionLeaseRepository repository = new StubLeaseRepository() {
            @Override
            public boolean renew(PipelineExecutionLease lease, Duration duration) {
                renewals.incrementAndGet();
                attempted.countDown();
                return false;
            }
        };
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        PipelineExecutionLease lease = lease();

        try (RenewingPipelineExecutionLeaseGuard guard =
                     new RenewingPipelineExecutionLeaseGuard(
                             repository, lease, Duration.ofSeconds(2),
                             Duration.ofMillis(10), scheduler)) {
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
            assertThrows(PipelineExecutionLeaseLostException.class,
                    guard::assertLeaseValid);
            assertEquals(1, renewals.get());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void shouldRejectRenewalIntervalNotShorterThanLease() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            assertThrows(IllegalArgumentException.class, () ->
                    new RenewingPipelineExecutionLeaseGuard(
                            new StubLeaseRepository(), lease(),
                            Duration.ofSeconds(1), Duration.ofSeconds(1), scheduler));
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static PipelineExecutionLease lease() {
        return new PipelineExecutionLease(
                "execution-1", "worker-1", Instant.now().plusSeconds(2), 1);
    }

    private static class StubLeaseRepository implements PipelineExecutionLeaseRepository {
        @Override
        public Optional<PipelineExecutionLease> tryAcquire(
                String executionId, String ownerId, Duration duration) {
            return Optional.empty();
        }

        @Override
        public boolean renew(PipelineExecutionLease lease, Duration duration) {
            return true;
        }

        @Override
        public boolean release(PipelineExecutionLease lease) {
            return true;
        }
    }
}
