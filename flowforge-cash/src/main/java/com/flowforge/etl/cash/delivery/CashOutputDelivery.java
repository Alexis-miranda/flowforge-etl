package com.flowforge.etl.cash.delivery;

/**
 * Immutable snapshot of a cash output delivery stored in SQL Server.
 */
public record CashOutputDelivery(
        String directory,
        String fileName,
        byte[] payload,
        String sha256,
        String status) {
}
