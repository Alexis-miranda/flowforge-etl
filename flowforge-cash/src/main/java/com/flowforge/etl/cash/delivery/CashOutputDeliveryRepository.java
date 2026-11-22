package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import java.util.List;

/** Persistence operations required by cash output delivery workflows. */
public interface CashOutputDeliveryRepository {
    List<String> findPendingSources();
    CashOutputDelivery findBySourceFile(String sourceFile);
    void markPublished(String sourceFile);

    /**
     * Stores the immutable artifact reference for a local execution.
     * Implementations should atomically update only a PENDING delivery.
     */
    boolean markPublished(String sourceFile, String executionId,
                          String publishedPath, String publishedSha256);

    /** Marks a delivery as published only while the supplied lease remains current. */
    boolean markPublished(String sourceFile, PipelineExecutionLease lease);

    /**
     * Stores the artifact reference only while the supplied lease remains current.
     * Implementations must evaluate the lease predicate in the same update.
     */
    boolean markPublished(String sourceFile, PipelineExecutionLease lease,
                          String publishedPath, String publishedSha256);

    void recordError(String sourceFile, String message);
    boolean recordError(String sourceFile, String message, PipelineExecutionLease lease);
}