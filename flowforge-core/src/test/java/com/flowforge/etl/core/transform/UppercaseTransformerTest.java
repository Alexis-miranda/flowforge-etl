package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.*;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;


import com.flowforge.etl.core.execution.PipelineExecutionResult;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.StepExecutor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class UppercaseTransformerTest {



    @Test
    void shouldTransformSelectedColumnToUppercase() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        List<Map<String, String>> records = List.of(
                Map.of("name", "Ana", "city", "Geneva"),
                Map.of("name", "Carlos", "city", "Madrid")
        );

        context.put("customers", records);

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        transformer.execute(step, context);

        List<Map<String, String>> result = context.get(
                "normalizedCustomers",
                List.class
        );

        assertEquals("ANA", result.get(0).get("name"));
        assertEquals("CARLOS", result.get(1).get("name"));
        assertEquals("Geneva", result.get(0).get("city"));
        assertEquals("Madrid", result.get(1).get("city"));
    }

    @Test
    void shouldNotModifyOriginalRecords() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        Map<String, String> originalRecord = Map.of(
                "name", "Ana",
                "city", "Geneva"
        );

        List<Map<String, String>> originalRecords = List.of(originalRecord);
        context.put("customers", originalRecords);

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        transformer.execute(step, context);

        assertEquals("Ana", originalRecord.get("name"));
        assertEquals("Geneva", originalRecord.get("city"));

        Object output = context.get("normalizedCustomers");

        assertInstanceOf(List.class, output);

        List<?> transformedRecords = (List<?>) output;

        assertInstanceOf(Map.class, transformedRecords.get(0));

        Map<?, ?> transformedRecord = (Map<?, ?>) transformedRecords.get(0);

        assertEquals("ANA", transformedRecord.get("name"));
    }

    @Test
    void shouldRejectMissingColumnConfiguration() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva")
        ));

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Missing or blank configuration property: column",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingInputData() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Missing input data in execution context: customers",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectInputThatIsNotAList() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("customers", "invalid input");

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Input data must be a list of records: customers",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRecordsThatAreNotMaps() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("customers", List.of("invalid record"));

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Each input record must be a map",
                exception.getMessage()
        );
    }


    @Test
    void shouldRejectRecordsWithNonStringValues() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        Map<String, Object> invalidRecord = Map.of(
                "name", "Ana",
                "age", 30
        );

        context.put("customers", List.of(invalidRecord));

        PipelineStep step = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Record keys and values must be strings",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingColumnInRecord() {
        UppercaseTransformer transformer = new UppercaseTransformer();
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("customers", List.of(
                Map.of("name", "Ana", "city", "Geneva")
        ));

        PipelineStep step = new PipelineStep(
                "uppercase-emails",
                "Uppercase customer emails",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "email"
                ))
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Column not found in input record: email",
                exception.getMessage()
        );
    }

    @Test
    void shouldSupportTransformStepType() {
        UppercaseTransformer transformer = new UppercaseTransformer();

        assertEquals(StepType.TRANSFORM, transformer.supportedType());
    }

    @Test
    void shouldExecuteTransformationThroughPipelineExecutor() {
        PipelineStep extractStep = new PipelineStep(
                "extract-customers",
                "Extract customers",
                StepType.EXTRACT,
                1,
                new StepConfiguration(Map.of())
        );

        PipelineStep transformStep = new PipelineStep(
                "uppercase-names",
                "Uppercase customer names",
                StepType.TRANSFORM,
                2,
                new StepConfiguration(Map.of(
                        "inputKey", "customers",
                        "outputKey", "normalizedCustomers",
                        "column", "name"
                ))
        );

        PipelineStep loadStep = new PipelineStep(
                "verify-customers",
                "Verify transformed customers",
                StepType.LOAD,
                3,
                new StepConfiguration(Map.of())
        );

        PipelineDefinition pipeline = new PipelineDefinition(
                "customer-pipeline",
                "Customer transformation",
                "Extracts, transforms and verifies customer data",
                List.of(extractStep, transformStep, loadStep)
        );

        StepExecutor testExtractor = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.EXTRACT;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    com.flowforge.etl.core.execution.PipelineExecutionContext context) {
                context.put("customers", List.of(
                        Map.of("name", "Ana", "city", "Geneva"),
                        Map.of("name", "Carlos", "city", "Madrid")
                ));
            }
        };

        StepExecutor testLoader = new StepExecutor() {
            @Override
            public StepType supportedType() {
                return StepType.LOAD;
            }

            @Override
            public void execute(
                    PipelineStep step,
                    com.flowforge.etl.core.execution.PipelineExecutionContext context) {
                Object output = context.get("normalizedCustomers");

                assertInstanceOf(List.class, output);

                List<?> records = (List<?>) output;

                assertEquals(2, records.size());
                assertEquals("ANA", ((Map<?, ?>) records.get(0)).get("name"));
                assertEquals("Geneva", ((Map<?, ?>) records.get(0)).get("city"));
                assertEquals("CARLOS", ((Map<?, ?>) records.get(1)).get("name"));
                assertEquals("Madrid", ((Map<?, ?>) records.get(1)).get("city"));
            }
        };

        PipelineExecutor executor = new PipelineExecutor(
                List.of(testExtractor, new UppercaseTransformer(), testLoader)
        );

        PipelineExecutionResult result = executor.execute(pipeline);

        assertEquals(PipelineStatus.SUCCEEDED, result.status());
        assertEquals(null, result.failedStepId());
    }
}
