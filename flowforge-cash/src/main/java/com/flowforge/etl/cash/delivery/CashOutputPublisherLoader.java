package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.StepExecutor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Coordinates publication of a persisted cash output delivery.
 */
public class CashOutputPublisherLoader implements StepExecutor {

    private final CashOutputDeliveryRepository deliveryRepository;
    private final LocalFilePublisher filePublisher;

    /**
     * Creates a publisher with explicit collaborators.
     * The constructor also allows the orchestration behavior to be tested
     * without requiring a live SQL Server instance.
     */
    public CashOutputPublisherLoader(
            CashOutputDeliveryRepository deliveryRepository,
            LocalFilePublisher filePublisher) {
        this.deliveryRepository = Objects.requireNonNull(
                deliveryRepository, "deliveryRepository must not be null");
        this.filePublisher = Objects.requireNonNull(
                filePublisher, "filePublisher must not be null");
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

        PipelineExecutionLease lease = context.executionLease().orElse(null);

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

            if (lease == null) {
                deliveryRepository.markPublished(sourceFile);
            } else if (!deliveryRepository.markPublished(sourceFile, lease)) {
                // Another valid worker may have completed the same immutable
                // delivery while this worker was publishing the same payload.
                CashOutputDelivery latest =
                        deliveryRepository.findBySourceFile(sourceFile);
                if (latest == null || !"PUBLISHED".equals(latest.status())) {
                    throw new IllegalStateException(
                            "Execution lease is no longer current; "
                                    + "cash delivery state was not changed");
                }
            }

            System.out.println("Published FRONT output: "
                    + publishedFile.toAbsolutePath().normalize());
        } catch (Exception exception) {
            if (lease == null) {
                deliveryRepository.recordError(sourceFile, exception.getMessage());
            } else {
                // This update is fenced too: an expired worker must not write
                // an error over the state owned by a newer execution.
                deliveryRepository.recordError(
                        sourceFile, exception.getMessage(), lease);
            }
            throw new IllegalStateException(
                    "Failed to publish FRONT output for " + sourceFile,
                    exception);
        }
    }
}
