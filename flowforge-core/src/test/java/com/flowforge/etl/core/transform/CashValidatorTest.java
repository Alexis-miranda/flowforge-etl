
package com.flowforge.etl.core.transform;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepConfiguration;
import com.flowforge.etl.core.domain.StepType;
import com.flowforge.etl.core.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashValidatorTest {

    private final CashValidator validator = new CashValidator();

    @Test
    void shouldSeparateValidAndRejectedCashRecords() {
        var step = step(Map.of(
                "inputKey", "cash",
                "validOutputKey", "valid",
                "rejectedOutputKey", "rejected"
        ));

        var context = new PipelineExecutionContext();
        context.put("cash", List.of(
                Map.of(
                        "accountCode", "ACC001",
                        "currency", "EUR",
                        "amount", "1250.50"
                ),
                Map.of(
                        "accountCode", "",
                        "currency", "EUR",
                        "amount", "100"
                ),
                Map.of(
                        "accountCode", "ACC003",
                        "currency", "XYZ",
                        "amount", "50"
                ),
                Map.of(
                        "accountCode", "ACC004",
                        "currency", "USD",
                        "amount", "not-a-number"
                )
        ));

        validator.execute(step, context);

        List<?> valid = context.get("valid", List.class);
        List<?> rejected = context.get("rejected", List.class);

        assertEquals(1, valid.size());
        assertEquals(3, rejected.size());

        assertEquals(
                "Missing accountCode",
                ((Map<?, ?>) rejected.get(0)).get("rejectionReason")
        );
        assertEquals(
                "Unsupported currency: XYZ",
                ((Map<?, ?>) rejected.get(1)).get("rejectionReason")
        );
        assertEquals(
                "Invalid amount: not-a-number",
                ((Map<?, ?>) rejected.get(2)).get("rejectionReason")
        );
    }

    @Test
    void shouldAcceptConfiguredCurrenciesCaseInsensitively() {
        var step = step(Map.of(
                "inputKey", "cash",
                "validOutputKey", "valid",
                "rejectedOutputKey", "rejected",
                "allowedCurrencies", "EUR,USD"
        ));

        var context = new PipelineExecutionContext();
        context.put("cash", List.of(
                Map.of(
                        "accountCode", "ACC001",
                        "currency", "eur",
                        "amount", "10"
                )
        ));

        validator.execute(step, context);

        assertEquals(1, context.get("valid", List.class).size());
        assertTrue(context.get("rejected", List.class).isEmpty());
    }

    @Test
    void shouldRejectWhenValidAndRejectedKeysAreEqual() {
        var step = step(Map.of(
                "inputKey", "cash",
                "validOutputKey", "result",
                "rejectedOutputKey", "result"
        ));

        var context = new PipelineExecutionContext();
        context.put("cash", List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.execute(step, context)
        );
    }

    private PipelineStep step(Map<String, String> properties) {
        return new PipelineStep(
                "cash-validation",
                "Validate cash records",
                StepType.TRANSFORM,
                1,
                new StepConfiguration(properties)
        );
    }
}
