
package com.flowforge.etl.connectors.integration;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerCashStagingLoader;
import com.flowforge.etl.connectors.file.csv.CsvExtractor;
import com.flowforge.etl.connectors.file.csv.CsvLoader;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.PipelineExecutionListener;
import com.flowforge.etl.core.execution.StepExecutor;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.core.transform.CashValidator;
import com.flowforge.etl.core.transform.UppercaseTransformer;

import java.util.List;

/**
 * Creates configured pipeline executors with the available ETL components.
 */
public final class PipelineExecutorFactory {

    private PipelineExecutorFactory() {
        // Utility class.
    }

    /**
     * Creates a pipeline executor with all built-in ETL components.
     */
    public static PipelineExecutor create() {
        return create(null, null);
    }

    /**
     * Creates a pipeline executor with an optional listener and repository.
     */
    public static PipelineExecutor create(
            PipelineExecutionListener listener,
            PipelineExecutionRepository repository) {

        List<StepExecutor> executors = List.of(
                new CsvExtractor(),
                new CashValidator(),
                new UppercaseTransformer(),
                new CsvLoader(),
                new SqlServerCashStagingLoader()
        );

        return new PipelineExecutor(executors, listener, repository);
    }
}
