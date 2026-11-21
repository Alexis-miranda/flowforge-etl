package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CashOutputRecoveryServiceTest {

    @Test
    void shouldReturnEmptyResultWhenThereAreNoPendingDeliveries() {
        FakeRepository repository = new FakeRepository(List.of());
        FakePublisher publisher = new FakePublisher();
        CashOutputRecoveryService service =
                new CashOutputRecoveryService(repository, publisher);

        CashOutputRecoveryService.RecoveryResult result =
                service.recoverPending();

        assertEquals(0, result.total());
        assertEquals(0, result.succeeded());
        assertEquals(0, result.failed());
        assertEquals(0, publisher.executedSources.size());
    }

    @Test
    void shouldRecoverAllPendingDeliveries() {
        FakeRepository repository =
                new FakeRepository(List.of("first.csv", "second.csv"));
        FakePublisher publisher = new FakePublisher();
        CashOutputRecoveryService service =
                new CashOutputRecoveryService(repository, publisher);

        CashOutputRecoveryService.RecoveryResult result =
                service.recoverPending();

        assertEquals(2, result.total());
        assertEquals(2, result.succeeded());
        assertEquals(0, result.failed());
        assertEquals(List.of("first.csv", "second.csv"),
                result.recoveredSources());
        assertEquals(List.of("first.csv", "second.csv"),
                publisher.executedSources);
    }

    @Test
    void shouldContinueRecoveringWhenOneDeliveryFails() {
        FakeRepository repository =
                new FakeRepository(List.of("good.csv", "bad.csv", "later.csv"));
        FakePublisher publisher = new FakePublisher();
        publisher.sourceToFail = "bad.csv";
        CashOutputRecoveryService service =
                new CashOutputRecoveryService(repository, publisher);

        CashOutputRecoveryService.RecoveryResult result =
                service.recoverPending();

        assertEquals(3, result.total());
        assertEquals(2, result.succeeded());
        assertEquals(1, result.failed());
        assertEquals(List.of("good.csv", "later.csv"),
                result.recoveredSources());
        assertEquals("bad.csv", result.failures().get(0).sourceFile());
        assertEquals("simulated failure",
                result.failures().get(0).message());
        assertEquals(3, publisher.executedSources.size());
    }

    private static final class FakeRepository
            implements CashOutputDeliveryRepository {

        private final List<String> pendingSources;

        private FakeRepository(List<String> pendingSources) {
            this.pendingSources = pendingSources;
        }

        @Override
        public List<String> findPendingSources() {
            return pendingSources;
        }

        @Override
        public CashOutputDelivery findBySourceFile(String sourceFile) {
            return null;
        }

        @Override
        public void markPublished(String sourceFile) {
        }

        @Override
        public void recordError(String sourceFile, String message) {
        }
    }

    private static final class FakePublisher implements StepExecutor {

        private final List<String> executedSources = new java.util.ArrayList<>();
        private String sourceToFail;

        @Override
        public StepType supportedType() {
            return StepType.LOAD;
        }

        @Override
        public String connectorKey() {
            return "cash-output-publisher";
        }

        @Override
        public void execute(
                PipelineStep step,
                PipelineExecutionContext context) {
            String sourceFile = step.configuration().get("sourceFile");
            executedSources.add(sourceFile);

            if (sourceFile.equals(sourceToFail)) {
                throw new IllegalStateException("simulated failure");
            }
        }
    }
}
