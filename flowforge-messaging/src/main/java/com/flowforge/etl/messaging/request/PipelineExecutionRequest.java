package com.flowforge.etl.messaging.request;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Describes a request to execute a registered pipeline.
 *
 * @param executionId unique identifier used to correlate and deduplicate an execution
 * @param pipelineType stable identifier of the pipeline to execute
 * @param parameters string-based parameters required to configure the pipeline
 * @param requestedAt time at which the execution was requested
 */
public record PipelineExecutionRequest(
        UUID executionId,
        String pipelineType,
        Map<String, String> parameters,
        Instant requestedAt
) {

    public PipelineExecutionRequest {
        Objects.requireNonNull(executionId, "Execution id must not be null");

        if (pipelineType == null || pipelineType.isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline type must not be blank");
        }

        Objects.requireNonNull(parameters, "Parameters must not be null");
        for (String key : parameters.keySet()) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException(
                        "Parameter keys must not be blank");
            }
        }
        parameters = Map.copyOf(parameters);

        Objects.requireNonNull(requestedAt, "Requested at must not be null");
    }
}
