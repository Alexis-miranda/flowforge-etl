package com.flowforge.etl.messaging.dispatch;

import com.flowforge.etl.messaging.outbox.PipelineExecutionOutboxMessage;
import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Performs one bounded pass over pending requests and publishes them.
 *
 * <p>A message is marked PUBLISHED only after the publisher returns successfully
 * (the RabbitMQ adapter waits for publisher confirmation). If publishing fails,
 * the failure is recorded and the message remains pending for a later pass.
 * A crash after broker confirmation but before the database update can cause
 * redelivery; consumers must therefore be idempotent.</p>
 */
public final class PipelineExecutionOutboxDispatcher {

    private final PipelineExecutionRequestOutbox outbox;
    private final PipelineExecutionRequestPublisher publisher;
    private final Clock clock;

    public PipelineExecutionOutboxDispatcher(
            PipelineExecutionRequestOutbox outbox,
            PipelineExecutionRequestPublisher publisher,
            Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "Outbox must not be null");
        this.publisher = Objects.requireNonNull(publisher, "Publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    /**
     * Reads and processes at most {@code batchSize} pending messages.
     * An interruption is recorded as a failed attempt, the interrupt flag is
     * restored, and no further messages are attempted in this pass.
     */
    public OutboxDispatchResult dispatchOnce(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be greater than zero");
        }

        List<PipelineExecutionOutboxMessage> messages = outbox.findPending(batchSize);
        int attempted = 0;
        int published = 0;
        int failed = 0;
        int interrupted = 0;

        for (PipelineExecutionOutboxMessage message : messages) {
            attempted++;
            try {
                publisher.publish(message.request());
                if (outbox.markPublished(message.messageId(), clock.instant())) {
                    published++;
                } else {
                    // The broker accepted the message but its outbox state changed
                    // concurrently. Do not claim a successful database transition.
                    failed++;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordFailure(message, exception);
                interrupted++;
                break;
            } catch (Exception exception) {
                recordFailure(message, exception);
                failed++;
            }
        }

        return new OutboxDispatchResult(attempted, published, failed, interrupted);
    }

    private void recordFailure(PipelineExecutionOutboxMessage message, Exception failure) {
        String detail = failure.getMessage();
        String error = failure.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : ": " + detail);
        outbox.recordPublishFailure(message.messageId(), error);
    }
}
