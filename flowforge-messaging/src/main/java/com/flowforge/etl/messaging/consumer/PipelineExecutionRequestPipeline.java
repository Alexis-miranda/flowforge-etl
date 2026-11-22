package com.flowforge.etl.messaging.consumer;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.execution.PipelineExecutor;

import java.util.Objects;

/** Runtime components required to execute one resolved pipeline type. */
public record PipelineExecutionRequestPipeline(
        PipelineDefinition definition,
        PipelineExecutor executor) {

    public PipelineExecutionRequestPipeline {
        Objects.requireNonNull(definition, "Pipeline definition must not be null");
        Objects.requireNonNull(executor, "Pipeline executor must not be null");
    }
}
