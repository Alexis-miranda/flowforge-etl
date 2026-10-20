
package com.flowforge.etl.core.repository;

import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPipelineExecutionRepositoryTest {

    private final InMemoryPipelineExecutionRepository repository =
            new InMemoryPipelineExecutionRepository();

    @Test
    void shouldSaveAndFindExecutionById() {
        PipelineExecutionResult result = createResult(
                "execution-1", "pipeline-1");

        repository.save(result);

        assertEquals(
                Optional.of(result),
                repository.findByExecutionId("execution-1")
        );
    }

    @Test
    void shouldReturnEmptyWhenExecutionDoesNotExist() {
        assertTrue(
                repository.findByExecutionId("unknown").isEmpty()
        );
    }

    @Test
    void shouldFindExecutionsByPipelineIdInInsertionOrder() {
        PipelineExecutionResult first = createResult(
                "execution-1", "pipeline-1");
        PipelineExecutionResult other = createResult(
                "execution-2", "pipeline-2");
        PipelineExecutionResult third = createResult(
                "execution-3", "pipeline-1");

        repository.save(first);
        repository.save(other);
        repository.save(third);

        assertEquals(
                List.of(first, third),
                repository.findByPipelineId("pipeline-1")
        );
    }

    @Test
    void shouldReturnEmptyListWhenPipelineHasNoExecutions() {
        assertEquals(
                List.of(),
                repository.findByPipelineId("unknown")
        );
    }

    @Test
    void shouldRejectNullResult() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(null)
        );
    }

    @Test
    void shouldRejectDuplicateExecutionId() {
        PipelineExecutionResult first = createResult(
                "execution-1", "pipeline-1");
        PipelineExecutionResult duplicate = createResult(
                "execution-1", "pipeline-2");

        repository.save(first);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(duplicate)
        );

        assertEquals(
                Optional.of(first),
                repository.findByExecutionId("execution-1")
        );
    }


    @Test
    void shouldRejectNullOrBlankExecutionId() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByExecutionId(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByExecutionId("")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByExecutionId("   ")
        );
    }

    @Test
    void shouldRejectNullOrBlankPipelineId() {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByPipelineId(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByPipelineId("")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findByPipelineId("   ")
        );
    }

    @Test
    void shouldRejectConcurrentDuplicateSaves() throws Exception {
        InMemoryPipelineExecutionRepository repository =
                new InMemoryPipelineExecutionRepository();

        PipelineExecutionResult result = new PipelineExecutionResult(
                "execution-1",
                "pipeline-1",
                PipelineStatus.SUCCEEDED,
                null,
                null,
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:01Z")
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    repository.save(result);
                    return true;
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            });

            Future<Boolean> second = executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    repository.save(result);
                    return true;
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertNotEquals(first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS));

            assertTrue(repository.findByExecutionId(result.executionId())
                    .isPresent());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }



    private PipelineExecutionResult createResult(
            String executionId,
            String pipelineId) {

        Instant startedAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant finishedAt = Instant.parse("2026-01-01T10:00:05Z");

        return new PipelineExecutionResult(
                executionId,
                pipelineId,
                PipelineStatus.SUCCEEDED,
                null,
                null,
                startedAt,
                finishedAt
        );
    }
}
