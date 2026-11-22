package com.flowforge.etl.api;

import com.flowforge.etl.messaging.outbox.PipelineExecutionRequestOutbox;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Accepts and durably enqueues pipeline execution requests. */
public final class ExecutionRequestService {

    private static final String SUPPORTED_PIPELINE_TYPE = "cash";

    private final PipelineExecutionRequestOutbox outbox;
    private final Clock clock;

    public ExecutionRequestService(PipelineExecutionRequestOutbox outbox, Clock clock) {
        this.outbox = Objects.requireNonNull(outbox, "Outbox must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public PipelineExecutionRequest submit(String pipelineType, Map<String, String> parameters) {
        if (!SUPPORTED_PIPELINE_TYPE.equals(pipelineType)) {
            throw new IllegalArgumentException(
                    "Unsupported pipeline type. Supported type: cash");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Parameters must not be null");
        }
        parameters.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException(
                        "Parameter keys must be non-blank and values must not be null");
            }
        });
        for (String required : java.util.List.of("input", "backOfficeCode", "outputDirectory")) {
            String value = parameters.get(required);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing required Cash request parameter: " + required);
            }
        }

        PipelineExecutionRequest request = new PipelineExecutionRequest(
                UUID.randomUUID(), pipelineType, parameters, clock.instant());
        outbox.enqueue(request);
        return request;
    }
}
