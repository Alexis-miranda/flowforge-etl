package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

/** Resolves a request's public pipeline type and parameters to configured runtime components. */
@FunctionalInterface
public interface PipelineExecutionRequestPipelineResolver {

    /**
     * @throws NonRetryablePipelineExecutionRequestException when the type or
     *         parameters are unsupported and retrying the same request cannot help
     */
    PipelineExecutionRequestPipeline resolve(PipelineExecutionRequest request);
}
