
package com.flowforge.etl.core.extract;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStatus;
import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import java.util.ArrayList;
import com.flowforge.etl.core.execution.StepExecutor;

import static org.junit.jupiter.api.Assertions.*;

class CsvExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExtractCsvRecordsIntoExecutionContext() throws Exception {
        Path csvFile = tempDir.resolve("customers.csv");

        Files.writeString(
                csvFile,
                "name,city\nAna,Geneva\nLuis,Madrid\n"
        );

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "path", csvFile.toString(),
                        "outputKey", "customers"
                ))
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        CsvExtractor extractor = new CsvExtractor();

        extractor.execute(step, context);

        List<?> result = (List<?>) context.get("customers");

        assertEquals(2, result.size());

        assertEquals(
                Map.of("name", "Ana", "city", "Geneva"),
                result.get(0)
        );

        assertEquals(
                Map.of("name", "Luis", "city", "Madrid"),
                result.get(1)
        );
    }

    @Test
    void shouldHandleCommasInsideQuotedFields() throws Exception {
        Path csvFile = tempDir.resolve("customers-with-commas.csv");

        Files.writeString(
                csvFile,
                "name,city\n\"Ana García\",\"Geneva, Switzerland\"\n"
        );

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "path", csvFile.toString(),
                        "outputKey", "customers"
                ))
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        CsvExtractor extractor = new CsvExtractor();

        extractor.execute(step, context);

        List<?> result = (List<?>) context.get("customers");

        assertEquals(1, result.size());
        assertEquals(
                Map.of(
                        "name", "Ana García",
                        "city", "Geneva, Switzerland"
                ),
                result.get(0)
        );
    }


    @Test
    void shouldRejectMissingPathConfiguration() {
        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "outputKey", "customers"
                ))
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        CsvExtractor extractor = new CsvExtractor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.execute(step, context)
        );

        assertEquals(
                "Missing or blank configuration property: path",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectMissingOutputKeyConfiguration() {
        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "path", "customers.csv"
                ))
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        CsvExtractor extractor = new CsvExtractor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.execute(step, context)
        );

        assertEquals(
                "Missing or blank configuration property: outputKey",
                exception.getMessage()
        );
    }

    @Test
    void shouldFailWhenCsvFileDoesNotExist() {
        Path missingFile = tempDir.resolve("missing.csv");

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "path", missingFile.toString(),
                        "outputKey", "customers"
                ))
        );

        PipelineExecutionContext context =
                new PipelineExecutionContext();

        CsvExtractor extractor = new CsvExtractor();

        java.io.UncheckedIOException exception = assertThrows(
                java.io.UncheckedIOException.class,
                () -> extractor.execute(step, context)
        );

        assertTrue(
                exception.getMessage().contains(
                        "Failed to read CSV file"
                )
        );
    }
    @Test
    void shouldSupportExtractStepType() {
        CsvExtractor extractor = new CsvExtractor();

        assertEquals(
                StepType.EXTRACT,
                extractor.supportedType()
        );
    }

    @Test
    void shouldExecuteCsvExtractionThroughPipelineExecutor()
            throws Exception {
        Path csvFile = tempDir.resolve("pipeline-customers.csv");

        Files.writeString(
                csvFile,
                "name,city\nAna,Geneva\nLuis,Madrid\n"
        );

        PipelineStep step = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of(
                        "path", csvFile.toString(),
                        "outputKey", "customers"
                ))
        );


        RecordVerificationExecutor verifier =
                new RecordVerificationExecutor();

        PipelineStep verificationStep = new PipelineStep(
                "verify-customers",
                "Verify extracted customers",
                StepType.TRANSFORM,
                2,
                new StepConfiguration(Map.of())
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "customer-pipeline",
                "Customer pipeline",
                "Extract and verify customer records",
                List.of(step, verificationStep)
        );

        PipelineExecutor executor = new PipelineExecutor(
                List.of(new CsvExtractor(), verifier)
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals("customer-pipeline", result.pipelineId());
        assertTrue(
                verifier.recordsVerified,
                "The verification step should inspect the extracted records"
        );
    }
    private static class RecordVerificationExecutor
            implements StepExecutor {

        private boolean recordsVerified;

        @Override
        public StepType supportedType() {
            return StepType.TRANSFORM;
        }

        @Override
        public void execute(
                PipelineStep step,
                PipelineExecutionContext context
        ) {
            List<?> records = (List<?>) context.get("customers");

            assertEquals(2, records.size());
            assertEquals(
                    Map.of("name", "Ana", "city", "Geneva"),
                    records.get(0)
            );
            assertEquals(
                    Map.of("name", "Luis", "city", "Madrid"),
                    records.get(1)
            );

            recordsVerified = true;
        }
    }
}
