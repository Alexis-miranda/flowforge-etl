package com.flowforge.etl.messaging.dispatch;

/** Summary of one bounded outbox dispatch pass. */
public record OutboxDispatchResult(
        int attempted,
        int published,
        int failed,
        int interrupted
) {
    public OutboxDispatchResult {
        if (attempted < 0 || published < 0 || failed < 0 || interrupted < 0
                || published + failed + interrupted > attempted) {
            throw new IllegalArgumentException("Invalid outbox dispatch counts");
        }
    }
}
