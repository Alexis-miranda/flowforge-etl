package com.flowforge.etl.messaging.consumer;

/** Indicates that a request is invalid and must not be requeued. */
public class NonRetryablePipelineExecutionRequestException extends RuntimeException {

    public NonRetryablePipelineExecutionRequestException(String message) {
        super(message);
    }

    public NonRetryablePipelineExecutionRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
