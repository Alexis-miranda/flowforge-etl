package com.flowforge.etl.messaging.dispatch;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/** Transport port for publishing an accepted pipeline execution request. */
@FunctionalInterface
public interface PipelineExecutionRequestPublisher {

    void publish(PipelineExecutionRequest request)
            throws IOException, InterruptedException, TimeoutException;
}
