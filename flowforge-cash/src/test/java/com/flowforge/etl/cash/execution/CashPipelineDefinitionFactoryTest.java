package com.flowforge.etl.cash.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashPipelineDefinitionFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCashPipelineWithExpectedOrderedSteps() {
        Path input = tempDir.resolve("cash.csv");
        Path outputDirectory = tempDir.resolve("output");

        PipelineDefinition pipeline = CashPipelineDefinitionFactory.create(
                input,
                "BNYM",
                outputDirectory,
                "accountCode,currency,amount",
                "cash-source.csv"
        );

        assertTrue(pipeline.id().startsWith("cash-"));
        assertEquals("Cash ETL", pipeline.name());
        assertEquals(5, pipeline.steps().size());

        List<PipelineStep> steps = pipeline.steps();

        assertEquals("extract-cash", steps.get(0).id());
        assertEquals(StepType.EXTRACT, steps.get(0).type());
        assertEquals(input.toString(), steps.get(0).configuration().get("path"));

        assertEquals("validate-cash", steps.get(1).id());
        assertEquals(StepType.TRANSFORM, steps.get(1).type());

        assertEquals("prepare-front-cash", steps.get(2).id());
        assertEquals(StepType.LOAD, steps.get(2).type());
        assertEquals(outputDirectory.toString(),
                steps.get(2).configuration().get("outputDirectory"));
        assertEquals("BNYM",
                steps.get(2).configuration().get("backOfficeCode"));
        assertEquals("accountCode,currency,amount",
                steps.get(2).configuration().get("columns"));
        assertTrue(steps.get(2).configuration().get("timestamp")
                .matches("\\d{8}_\\d{6}"));

        assertEquals("persist-cash", steps.get(3).id());
        assertEquals("cash-source.csv",
                steps.get(3).configuration().get("sourceFile"));
        assertEquals("BNYM",
                steps.get(3).configuration().get("backOfficeCode"));

        assertEquals("publish-front-cash", steps.get(4).id());
        assertEquals("cash-source.csv",
                steps.get(4).configuration().get("sourceFile"));
    }

    @Test
    void shouldPreserveCallerSuppliedPipelineId() {
        PipelineDefinition first = CashPipelineDefinitionFactory.create(
                "cash-etl", tempDir.resolve("cash.csv"), "BNYM",
                tempDir.resolve("output"), "accountCode,currency,amount", "cash.csv");
        PipelineDefinition second = CashPipelineDefinitionFactory.create(
                "cash-etl", tempDir.resolve("cash.csv"), "BNYM",
                tempDir.resolve("output"), "accountCode,currency,amount", "cash.csv");

        assertEquals("cash-etl", first.id());
        assertEquals(first.id(), second.id());
    }

    @Test
    void shouldGenerateDifferentPipelineIdsForSeparateRuns() {
        PipelineDefinition first = createPipeline();
        PipelineDefinition second = createPipeline();

        assertNotEquals(first.id(), second.id());
    }

    private PipelineDefinition createPipeline() {
        return CashPipelineDefinitionFactory.create(
                tempDir.resolve("cash.csv"),
                "BNYM",
                tempDir.resolve("output"),
                "accountCode,currency,amount",
                "cash-source.csv"
        );
    }
}
