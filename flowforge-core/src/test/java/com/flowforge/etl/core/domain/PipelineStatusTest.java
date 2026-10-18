
package com.flowforge.etl.core.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link PipelineStatus}.
 */
class PipelineStatusTest {

    @Test
    void shouldDefineAllExpectedStatuses() {
        assertEquals(4, PipelineStatus.values().length);
    }

    @Test
    void shouldExposeAllLifecycleStatuses() {
        assertNotNull(PipelineStatus.valueOf("CREATED"));
        assertNotNull(PipelineStatus.valueOf("RUNNING"));
        assertNotNull(PipelineStatus.valueOf("SUCCEEDED"));
        assertNotNull(PipelineStatus.valueOf("FAILED"));
    }
}
