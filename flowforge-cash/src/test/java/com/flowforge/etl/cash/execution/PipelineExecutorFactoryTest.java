package com.flowforge.etl.cash.execution;

import com.flowforge.etl.core.execution.PipelineExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PipelineExecutorFactoryTest {

    @Test
    void shouldCreatePipelineExecutorWithBuiltInComponents() {
        PipelineExecutor executor = PipelineExecutorFactory.create();

        assertNotNull(executor);
    }

}