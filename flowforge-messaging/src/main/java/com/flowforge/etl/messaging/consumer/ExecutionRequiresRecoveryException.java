package com.flowforge.etl.messaging.consumer;

/**
 * Indicates that a previously started execution needs recovery assessment
 * before its duplicate request can be safely acknowledged.
 *
 * <p>This is non-retryable at the request-consumer level so the message is
 * routed to the dead-letter queue for controlled inspection rather than
 * repeatedly starting or acknowledging an interrupted execution.</p>
 */
public final class ExecutionRequiresRecoveryException
        extends NonRetryablePipelineExecutionRequestException {

    public ExecutionRequiresRecoveryException(String executionId) {
        super("Execution is still RUNNING and requires recovery assessment: " + executionId);
    }
}
