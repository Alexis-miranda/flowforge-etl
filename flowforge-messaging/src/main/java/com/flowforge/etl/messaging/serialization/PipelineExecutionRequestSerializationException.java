package com.flowforge.etl.messaging.serialization;

/** Indicates that a pipeline execution request could not be converted to or from JSON. */
public class PipelineExecutionRequestSerializationException extends RuntimeException {

    public PipelineExecutionRequestSerializationException(String message) {
        super(message);
    }

    public PipelineExecutionRequestSerializationException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
