package com.flowforge.etl.cash.delivery;

import java.util.List;

/**
 * Persistence operations required by cash output delivery workflows.
 */
public interface CashOutputDeliveryRepository {

    List<String> findPendingSources();

    CashOutputDelivery findBySourceFile(String sourceFile);

    void markPublished(String sourceFile);

    void recordError(String sourceFile, String message);
}
