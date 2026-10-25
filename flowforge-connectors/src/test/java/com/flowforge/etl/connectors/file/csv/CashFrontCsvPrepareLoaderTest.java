package com.flowforge.etl.connectors.file.csv;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashFrontCsvPrepareLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPreparePayloadHashAndPathWithoutWritingOutput()
            throws Exception {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("validCash", List.of(
                Map.of(
                        "accountCode", "A1",
                        "currency", "EUR",
                        "amount", "12.50"
                )
        ));

        PipelineStep step = new PipelineStep(
                "prepare-front",
                "Prepare FRONT CSV",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of(
                        "connector", "cash-front-csv-prepare",
                        "inputKey", "validCash",
                        "outputDirectory", tempDir.toString(),
                        "backOfficeCode", "TEST",
                        "columns", "accountCode,currency,amount",
                        "timestamp", "20261025_130000"
                ))
        );

        new CashFrontCsvPrepareLoader().execute(step, context);

        byte[] payload = (byte[]) context.get("cashFrontPayload");
        String csv = new String(payload, StandardCharsets.UTF_8);
        String expectedPath = tempDir.toAbsolutePath().normalize()
                .resolve("newcash_TEST_20261025_130000.csv")
                .toString();

        assertEquals(
                "accountCode,currency,amount\r\nA1,EUR,12.50\r\n",
                csv
        );
        assertEquals(expectedPath, context.get("cashFrontOutputPath"));
        assertEquals("TEST", context.get("cashFrontBackOfficeCode"));
        assertEquals(
                tempDir.toAbsolutePath().normalize().toString(),
                context.get("cashFrontOutputDirectory")
        );
        assertEquals(
                "newcash_TEST_20261025_130000.csv",
                context.get("cashFrontOutputFileName")
        );

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(payload);
        StringBuilder expectedHash = new StringBuilder();
        for (byte value : digest) {
            expectedHash.append(String.format("%02x", value & 0xff));
        }

        assertEquals(expectedHash.toString(), context.get("cashFrontSha256"));
        assertTrue(Files.isDirectory(tempDir));
        assertFalse(Files.exists(Path.of(expectedPath)));
    }
}