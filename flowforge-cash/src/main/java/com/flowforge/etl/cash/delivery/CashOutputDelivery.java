package com.flowforge.etl.cash.delivery;

/** Immutable snapshot of a cash output delivery stored in SQL Server. */
public record CashOutputDelivery(
        String directory,
        String fileName,
        byte[] payload,
        String sha256,
        String status,
        String publishedPath,
        String publishedSha256,
        String publishedExecutionId) {

    /** Backwards-compatible constructor for deliveries not yet published. */
    public CashOutputDelivery(String directory, String fileName, byte[] payload,
                              String sha256, String status) {
        this(directory, fileName, payload, sha256, status, null, null, null);
    }
}