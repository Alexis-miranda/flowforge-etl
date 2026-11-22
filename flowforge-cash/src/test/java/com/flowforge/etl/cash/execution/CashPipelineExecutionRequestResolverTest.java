package com.flowforge.etl.cash.execution;

import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.messaging.consumer.NonRetryablePipelineExecutionRequestException;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CashPipelineExecutionRequestResolverTest {

    @TempDir
    Path tempDir;

    private final PipelineExecutor executor = new PipelineExecutor(java.util.List.of());

    @Test
    void shouldResolveCashRequestToStablePipelineDefinition() throws Exception {
        Path input = Files.writeString(tempDir.resolve("cash.csv"),
                "accountCode,adjustmentId,currency,amount\\nA1,ADJ1,CHF,10.00\\n");
        Path output = tempDir.resolve("output");
        CashPipelineExecutionRequestResolver resolver =
                new CashPipelineExecutionRequestResolver(executor);

        var first = resolver.resolve(request("cash", Map.of(
                "input", input.toString(),
                "backOfficeCode", "BNYM",
                "outputDirectory", output.toString())));
        var second = resolver.resolve(request("cash", Map.of(
                "input", input.toString(),
                "backOfficeCode", "BNYM",
                "outputDirectory", output.toString())));

        assertEquals(CashPipelineExecutionRequestResolver.PIPELINE_ID,
                first.definition().id());
        assertEquals(first.definition().id(), second.definition().id());
        assertSame(executor, first.executor());
        assertEquals(input.toAbsolutePath().normalize().toString(),
                first.definition().steps().get(0).configuration().get("path"));
        assertEquals("accountCode,currency,amount",
                first.definition().steps().get(2).configuration().get("columns"));
        assertEquals("cash.csv",
                first.definition().steps().get(3).configuration().get("sourceFile"));
    }

    @Test
    void shouldUseOptionalColumnsAndSourceFileParameters() throws Exception {
        Path input = Files.writeString(tempDir.resolve("input.csv"), "header\\n");
        var resolved = new CashPipelineExecutionRequestResolver(executor).resolve(
                request("cash", Map.of(
                        "input", input.toString(),
                        "backOfficeCode", "BNYM",
                        "outputDirectory", tempDir.resolve("out").toString(),
                        "columns", "currency,amount",
                        "sourceFile", "delivery-01.csv")));

        assertEquals("currency,amount",
                resolved.definition().steps().get(2).configuration().get("columns"));
        assertEquals("delivery-01.csv",
                resolved.definition().steps().get(3).configuration().get("sourceFile"));
    }

    @Test
    void shouldRejectUnknownPipelineType() {
        var resolver = new CashPipelineExecutionRequestResolver(executor);
        assertThrows(NonRetryablePipelineExecutionRequestException.class,
                () -> resolver.resolve(request("unknown", Map.of())));
    }

    @Test
    void shouldRejectUnknownParameters() throws Exception {
        Path input = Files.writeString(tempDir.resolve("cash.csv"), "header\\n");
        var resolver = new CashPipelineExecutionRequestResolver(executor);

        assertThrows(NonRetryablePipelineExecutionRequestException.class,
                () -> resolver.resolve(request("cash", Map.of(
                        "input", input.toString(),
                        "backOfficeCode", "BNYM",
                        "outputDirectory", tempDir.resolve("out").toString(),
                        "unexpected", "value"))));
    }

    @Test
    void shouldRejectMissingOrUnreadableInputFile() {
        var resolver = new CashPipelineExecutionRequestResolver(executor);
        assertThrows(NonRetryablePipelineExecutionRequestException.class,
                () -> resolver.resolve(request("cash", Map.of(
                        "input", tempDir.resolve("missing.csv").toString(),
                        "backOfficeCode", "BNYM",
                        "outputDirectory", tempDir.resolve("out").toString()))));
    }

    @Test
    void shouldRejectInvalidBackOfficeCode() throws Exception {
        Path input = Files.writeString(tempDir.resolve("cash.csv"), "header\\n");
        var resolver = new CashPipelineExecutionRequestResolver(executor);

        assertThrows(NonRetryablePipelineExecutionRequestException.class,
                () -> resolver.resolve(request("cash", Map.of(
                        "input", input.toString(),
                        "backOfficeCode", "BNYM/../OTHER",
                        "outputDirectory", tempDir.resolve("out").toString()))));
    }

    private static PipelineExecutionRequest request(
            String pipelineType, Map<String, String> parameters) {
        return new PipelineExecutionRequest(
                UUID.randomUUID(), pipelineType, parameters, Instant.now());
    }
}
