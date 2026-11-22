package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.execution.PipelineExecutionLease;
import java.util.List;

/** Persistence operations required by cash output delivery workflows. */
public interface CashOutputDeliveryRepository {
    List<String> findPendingSources();
    CashOutputDelivery findBySourceFile(String sourceFile);

    /** Marks a delivery as published for a locally managed legacy execution. */
    void markPublished(String sourceFile);

    /** Atomically stores the immutable artifact reference for a local execution. */
    boolean markPublished(String sourceFile, String executionId,
                          String publishedPath, String publishedSha256);

    /** Marks a delivery as published only while the supplied lease remains current. */
    boolean markPublished(String sourceFile, PipelineExecutionLease lease);

    /** Atomically stores the artifact reference while the supplied lease remains current. */
    boolean markPublished(String sourceFile, PipelineExecutionLease lease,
                          String publishedPath, String publishedSha256);

    /** Records an error for a locally managed execution. */
    void recordError(String sourceFile, String message);

    /** Records an error only while the supplied execution lease remains current. */
    boolean recordError(String sourceFile, String message, PipelineExecutionLease lease);
}