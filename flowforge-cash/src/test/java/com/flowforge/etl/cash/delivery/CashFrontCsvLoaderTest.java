
package com.flowforge.etl.cash.delivery;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashFrontCsvLoaderTest {

    @TempDir
    Path tempDir;

    private final CashFrontCsvLoader loader = new CashFrontCsvLoader();

    @Test
    void shouldWriteFrontCsvWithConfiguredColumnOrder() throws Exception {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("validCash", List.of(
                Map.of(
                        "currency", "EUR",
                        "amount", "125.50",
                        "accountCode", "ACC01"
                )
        ));

        loader.execute(createStep(), context);

        Path output = Path.of(
                context.get("cashFrontOutputPath", String.class)
        );

        assertEquals(
                "newcash_BNYM_20261016_213000.csv",
                output.getFileName().toString()
        );

        try (CSVParser parser = CSVParser.parse(
                output,
                StandardCharsets.UTF_8,
                CSVFormat.RFC4180.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .get()
        )) {
            assertEquals(
                    List.of("accountCode", "currency", "amount"),
                    parser.getHeaderNames()
            );

            List<CSVRecord> rows = parser.getRecords();
            assertEquals(1, rows.size());
            assertEquals("ACC01", rows.get(0).get("accountCode"));
            assertEquals("EUR", rows.get(0).get("currency"));
            assertEquals("125.50", rows.get(0).get("amount"));
        }
    }

    @Test
    void shouldCreateOutputDirectory() {
        Path directory = tempDir.resolve("nested/front");

        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("validCash", List.of(Map.of(
                "accountCode", "ACC01",
                "currency", "EUR",
                "amount", "10.00"
        )));

        loader.execute(createStep(directory), context);

        assertTrue(Files.isDirectory(directory));
        assertTrue(Files.exists(
                Path.of(context.get("cashFrontOutputPath", String.class))
        ));
    }

    @Test
    void shouldRejectMissingConfiguredColumn() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("validCash", List.of(Map.of(
                "accountCode", "ACC01",
                "currency", "EUR"
        )));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(createStep(), context)
        );

        assertEquals(
                "Missing FRONT output column: amount",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectEmptyInput() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.put("validCash", List.of());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.execute(createStep(), context)
        );

        assertEquals(
                "Cannot write FRONT CSV without records",
                exception.getMessage()
        );
    }

    private PipelineStep createStep() {
        return createStep(tempDir);
    }

    private PipelineStep createStep(Path directory) {
        return new PipelineStep(
                "cash-front-output",
                "Generate FRONT cash file",
                StepType.LOAD,
                1,
                new StepConfiguration(Map.of(
                        "outputDirectory", directory.toString(),
                        "inputKey", "validCash",
                        "backOfficeCode", "BNYM",
                        "columns", "accountCode,currency,amount",
                        "delimiter", ",",
                        "timestamp", "20261016_213000"
                ))
        );
    }
}
