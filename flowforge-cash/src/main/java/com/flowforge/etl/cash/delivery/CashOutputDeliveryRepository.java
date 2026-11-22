package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.execution.PipelineExecutionLease;

import java.util.List;

/**
 * Persistence operations required by cash output delivery workflows.
 */
public interface CashOutputDeliveryRepository {

    List<String> findPendingSources();

    CashOutputDelivery findBySourceFile(String sourceFile);

    /** Marks a delivery as published for a locally managed execution. */
    void markPublished(String sourceFile);

    /**
     * Marks a delivery as published only while the supplied execution lease
     * remains current in the authoritative store.
     *
     * @return true when the pending delivery was updated; false when the lease
     *         is stale or the delivery is no longer pending
     */
    boolean markPublished(String sourceFile, PipelineExecutionLease lease);

    /** Records an error for a locally managed execution. */
    void recordError(String sourceFile, String message);

    /**
     * Records an error only while the supplied execution lease remains current.
     *
     * @return true when the pending delivery was updated; false when the lease
     *         is stale or the delivery is no longer pending
     */
    boolean recordError(
            String sourceFile, String message, PipelineExecutionLease lease);
}
