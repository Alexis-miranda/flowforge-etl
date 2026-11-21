package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Coordinates retries of persisted cash output deliveries.
 */
public class CashOutputRecoveryService {

    private final CashOutputDeliveryRepository deliveryRepository;
    private final StepExecutor publisher;

    /**
     * Creates the recovery service with the production SQL Server and local
     * filesystem adapters.
     */
    public CashOutputRecoveryService() {
        SqlServerCashOutputDeliveryRepository repository =
                new SqlServerCashOutputDeliveryRepository();
        this.deliveryRepository = repository;
        this.publisher = new CashOutputPublisherLoader(
                repository, new LocalFilePublisher());
    }

    /**
     * Creates the service with explicit collaborators for testing and reuse.
     */
    public CashOutputRecoveryService(
            CashOutputDeliveryRepository deliveryRepository,
            StepExecutor publisher) {
        this.deliveryRepository = deliveryRepository;
        this.publisher = publisher;
    }

    public RecoveryResult recoverPending() {
        List<String> pendingSources =
                deliveryRepository.findPendingSources();
        List<String> recoveredSources = new ArrayList<>();
        List<RecoveryFailure> failures = new ArrayList<>();

        for (String sourceFile : pendingSources) {
            try {
                PipelineStep step = new PipelineStep(
                        "recover-front-output",
                        "Recover FRONT output",
                        StepType.LOAD,
                        1,
                        new StepConfiguration(Map.of(
                                "connector", "cash-output-publisher",
                                "sourceFile", sourceFile
                        ))
                );

                publisher.execute(step, new PipelineExecutionContext());
                recoveredSources.add(sourceFile);
            } catch (RuntimeException exception) {
                failures.add(new RecoveryFailure(
                        sourceFile, exception.getMessage()));
            }
        }

        return new RecoveryResult(
                pendingSources.size(),
                recoveredSources,
                failures);
    }

    public record RecoveryResult(
            int total,
            List<String> recoveredSources,
            List<RecoveryFailure> failures) {

        public RecoveryResult {
            recoveredSources = List.copyOf(recoveredSources);
            failures = List.copyOf(failures);
        }

        public int succeeded() {
            return recoveredSources.size();
        }

        public int failed() {
            return failures.size();
        }
    }

    public record RecoveryFailure(String sourceFile, String message) {
    }
}
