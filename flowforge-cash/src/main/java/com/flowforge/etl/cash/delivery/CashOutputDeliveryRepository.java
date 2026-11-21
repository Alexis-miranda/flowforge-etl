package com.flowforge.etl.cash.delivery;

/**
 * Persistence operations required by the cash output publisher.
 */
public interface CashOutputDeliveryRepository {

    CashOutputDelivery findBySourceFile(String sourceFile);

    void markPublished(String sourceFile);

    void recordError(String sourceFile, String message);
}
