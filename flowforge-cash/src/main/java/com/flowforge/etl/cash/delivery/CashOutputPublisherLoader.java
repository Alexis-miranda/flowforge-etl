package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import com.flowforge.etl.core.execution.PipelineExecutionLease;
import com.flowforge.etl.core.execution.StepExecutor;

import java.nio.file.Path;
import java.util.Objects;

/** Coordinates publication of a persisted cash output delivery. */
public class CashOutputPublisherLoader implements StepExecutor {
    private final CashOutputDeliveryRepository deliveryRepository;
    private final LocalFilePublisher filePublisher;

    public CashOutputPublisherLoader(CashOutputDeliveryRepository deliveryRepository,
                                     LocalFilePublisher filePublisher) {
        this.deliveryRepository = Objects.requireNonNull(
                deliveryRepository, "deliveryRepository must not be null");
        this.filePublisher = Objects.requireNonNull(
                filePublisher, "filePublisher must not be null");
    }

    @Override public StepType supportedType() { return StepType.LOAD; }
    @Override public String connectorKey() { return "cash-output-publisher"; }

    @Override
    public void execute(PipelineStep step, PipelineExecutionContext context) {
        String sourceFile = step.configuration().get("sourceFile");
        if (sourceFile == null || sourceFile.isBlank()) {
            throw new IllegalArgumentException("Missing required configuration: sourceFile");
        }

        PipelineExecutionLease lease = context.executionLease().orElse(null);
        String executionId = context.executionId().orElse(null);
        CashOutputDelivery delivery = deliveryRepository.findBySourceFile(sourceFile);
        if (delivery == null) {
            throw new IllegalStateException("No output delivery found for source: " + sourceFile);
        }
        if ("PUBLISHED".equals(delivery.status())) return;

        try {
            Path publishedFile = executionId == null
                    ? filePublisher.publish(delivery)
                    : filePublisher.publish(delivery, executionId);

            String publishedHash = delivery.sha256();
            boolean updated;
            if (lease != null) {
                updated = deliveryRepository.markPublished(
                        sourceFile, lease, publishedFile.toString(), publishedHash);
            } else if (executionId != null) {
                updated = deliveryRepository.markPublished(
                        sourceFile, executionId, publishedFile.toString(), publishedHash);
            } else {
                deliveryRepository.markPublished(sourceFile);
                updated = true;
            }

            if (!updated) {
                CashOutputDelivery latest = deliveryRepository.findBySourceFile(sourceFile);
                boolean samePublishedArtifact = latest != null
                        && "PUBLISHED".equals(latest.status())
                        && publishedHash.equals(latest.publishedSha256())
                        && publishedFile.toString().equals(latest.publishedPath());
                if (!samePublishedArtifact) {
                    throw new IllegalStateException(
                            "Execution no longer owns the pending delivery; "
                                    + "published artifact was not registered");
                }
            }

            System.out.println("Published FRONT output: "
                    + publishedFile.toAbsolutePath().normalize());
        } catch (Exception exception) {
            if (lease == null) {
                deliveryRepository.recordError(sourceFile, exception.getMessage());
            } else {
                deliveryRepository.recordError(sourceFile, exception.getMessage(), lease);
            }
            throw new IllegalStateException(
                    "Failed to publish FRONT output for " + sourceFile, exception);
        }
    }
}