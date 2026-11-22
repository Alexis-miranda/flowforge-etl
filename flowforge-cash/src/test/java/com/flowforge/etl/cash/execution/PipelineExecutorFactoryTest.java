package com.flowforge.etl.cash.execution;

import com.flowforge.etl.cash.delivery.CashOutputDelivery;
import com.flowforge.etl.cash.delivery.CashOutputDeliveryRepository;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.StepExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecutorFactoryTest {

    @Test
    void shouldCreatePipelineExecutorWithBuiltInComponents() {
        PipelineExecutor executor = PipelineExecutorFactory.create();

        assertNotNull(executor);
    }

    @Test
    void shouldIncludeCashOutputPublisherInBuiltInExecutors() {
        CashOutputDeliveryRepository repository = new FakeDeliveryRepository();

        List<StepExecutor> executors =
                PipelineExecutorFactory.createExecutors(repository);

        assertEquals(9, executors.size());
        assertTrue(executors.stream().anyMatch(executor ->
                executor.supportedType() == StepType.LOAD
                        && "cash-output-publisher".equals(executor.connectorKey())));
    }

    @Test
    void shouldRequireDeliveryRepositoryWhenBuildingExecutors() {
        assertThrows(NullPointerException.class,
                () -> PipelineExecutorFactory.createExecutors(null));
    }

    private static final class FakeDeliveryRepository
            implements CashOutputDeliveryRepository {

        @Override
        public List<String> findPendingSources() {
            return List.of();
        }

        @Override
        public CashOutputDelivery findBySourceFile(String sourceFile) {
            return null;
        }

        @Override
        public void markPublished(String sourceFile) {
        }

        @Override
        public boolean markPublished(
                String sourceFile, PipelineExecutionLease lease) {
            return false;
        }

        @Override
        public void recordError(String sourceFile, String message) {
        }

        @Override
        public boolean recordError(
                String sourceFile, String message, PipelineExecutionLease lease) {
            return false;
        }
    }
}
