package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColumnMappingTransformerTest {

    private final ColumnMappingTransformer transformer =
            new ColumnMappingTransformer();

    @Test
    void shouldMapSourceColumnsToCanonicalNames() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("cashRecords", List.of(
                Map.of(
                        "PORTFOLIO", "ACC123",
                        "CURRENCY", "EUR",
                        "AMOUNT", "1250.50"
                )
        ));

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode,CURRENCY=currency,AMOUNT=amount"
        ));

        transformer.execute(step, context);

        List<?> result = context.get("mappedCash", List.class);

        assertEquals(1, result.size());

        Map<?, ?> row = (Map<?, ?>) result.get(0);

        assertEquals("ACC123", row.get("accountCode"));
        assertEquals("EUR", row.get("currency"));
        assertEquals("1250.50", row.get("amount"));

        assertFalse(row.containsKey("PORTFOLIO"));
        assertFalse(row.containsKey("AMOUNT"));
    }

    @Test
    void shouldPreserveUnmappedColumns() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("cashRecords", List.of(
                Map.of(
                        "PORTFOLIO", "ACC123",
                        "CURRENCY", "EUR",
                        "DESCRIPTION", "Daily cash"
                )
        ));

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode,CURRENCY=currency"
        ));

        transformer.execute(step, context);

        List<?> result = context.get("mappedCash", List.class);
        Map<?, ?> row = (Map<?, ?>) result.get(0);

        assertEquals("ACC123", row.get("accountCode"));
        assertEquals("EUR", row.get("currency"));
        assertEquals("Daily cash", row.get("DESCRIPTION"));
    }

    @Test
    void shouldNotModifyOriginalRecords() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        Map<String, String> original = Map.of(
                "PORTFOLIO", "ACC123",
                "AMOUNT", "100.00"
        );

        context.put("cashRecords", List.of(original));

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode,AMOUNT=amount"
        ));

        transformer.execute(step, context);

        assertEquals("ACC123", original.get("PORTFOLIO"));
        assertEquals("100.00", original.get("AMOUNT"));

        Map<?, ?> mapped =
                (Map<?, ?>) ((List<?>) context.get("mappedCash")).get(0);

        assertEquals("ACC123", mapped.get("accountCode"));
    }

    @Test
    void shouldRejectMissingMappedSourceColumn() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("cashRecords", List.of(
                Map.of("PORTFOLIO", "ACC123")
        ));

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode,AMOUNT=amount"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Mapped source column not found: AMOUNT",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateTargetColumns() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        context.put("cashRecords", List.of(
                Map.of(
                        "PORTFOLIO", "ACC123",
                        "ACCOUNT", "ACC456"
                )
        ));

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode,ACCOUNT=accountCode"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Multiple source columns map to the same target: accountCode",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMalformedMappingConfiguration() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Each mapping must use source=target format: PORTFOLIO",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMissingInputData() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "mappedCash",
                "mappings", "PORTFOLIO=accountCode"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "Missing input data in execution context: cashRecords",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSameInputAndOutputKey() {
        PipelineExecutionContext context = new PipelineExecutionContext();

        PipelineStep step = step(Map.of(
                "inputKey", "cashRecords",
                "outputKey", "cashRecords",
                "mappings", "PORTFOLIO=accountCode"
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transformer.execute(step, context)
        );

        assertEquals(
                "inputKey and outputKey must be different",
                exception.getMessage()
        );
    }

    @Test
    void shouldSupportTransformStepType() {
        assertEquals(StepType.TRANSFORM, transformer.supportedType());
        assertEquals("column-mapping", transformer.connectorKey());
    }

    private PipelineStep step(Map<String, String> configuration) {
        return new PipelineStep(
                "map-cash-columns",
                "Map cash source columns",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(configuration)
        );
    }
}