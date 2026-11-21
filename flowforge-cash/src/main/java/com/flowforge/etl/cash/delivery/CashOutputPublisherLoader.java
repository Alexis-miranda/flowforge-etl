package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.StepExecutor;

import java.nio.file.Path;

/**
 * Coordinates publication of a persisted cash output delivery.
 */
public class CashOutputPublisherLoader implements StepExecutor {

    private final CashOutputDeliveryRepository deliveryRepository;
    private final LocalFilePublisher filePublisher;

    /**
     * Creates the production publisher with SQL Server and local filesystem
     * adapters.
     */
    public CashOutputPublisherLoader() {
        this(new SqlServerCashOutputDeliveryRepository(),
                new LocalFilePublisher());
    }

    /**
     * Creates a publisher with explicit collaborators.
     * The constructor also allows the orchestration behavior to be tested
     * without requiring a live SQL Server instance.
     */
    public CashOutputPublisherLoader(
            CashOutputDeliveryRepository deliveryRepository,
            LocalFilePublisher filePublisher) {
        this.deliveryRepository = deliveryRepository;
        this.filePublisher = filePublisher;
    }

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

        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required configuration: sourceFile");
        }

        CashOutputDelivery delivery =
                deliveryRepository.findBySourceFile(sourceFile);

        if (delivery == null) {
            throw new IllegalStateException(
                    "No output delivery found for source: " + sourceFile);
        }

        if ("PUBLISHED".equals(delivery.status())) {
            return;
        }

        try {
            Path publishedFile = filePublisher.publish(delivery);
            deliveryRepository.markPublished(sourceFile);

            System.out.println("Published FRONT output: "
                    + publishedFile.toAbsolutePath().normalize());
        } catch (Exception exception) {
            deliveryRepository.recordError(sourceFile, exception.getMessage());
            throw new IllegalStateException(
                    "Failed to publish FRONT output for " + sourceFile,
                    exception);
        }
    }
}
