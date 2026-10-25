package com.flowforge.etl.connectors.integration;

import com.flowforge.etl.connectors.database.sqlserver.SqlServerCashStagingLoader;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerConnectionFactory;
import com.flowforge.etl.connectors.database.sqlserver.SqlServerPipelineExecutionRepository;
import com.flowforge.etl.connectors.file.csv.CashFrontCsvLoader;
import com.flowforge.etl.connectors.file.csv.CashFrontCsvPrepareLoader;
import com.flowforge.etl.connectors.file.csv.CashOutputPublisherLoader;
import com.flowforge.etl.connectors.file.csv.CsvExtractor;
import com.flowforge.etl.connectors.file.csv.CsvLoader;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.core.execution.PipelineExecutionListener;
import com.flowforge.etl.core.execution.StepExecutor;
import com.flowforge.etl.core.repository.PipelineExecutionRepository;
import com.flowforge.etl.core.transform.CashValidator;
import com.flowforge.etl.core.transform.ColumnMappingTransformer;
import com.flowforge.etl.core.transform.UppercaseTransformer;

import java.util.List;

public final class PipelineExecutorFactory {

    private PipelineExecutorFactory() {
    }

    public static PipelineExecutor create() {
        PipelineExecutionRepository repository =
                SqlServerConnectionFactory.isConfigured()
                        ? new SqlServerPipelineExecutionRepository()
                        : null;

        return create(null, repository);
    }

    public static PipelineExecutor create(
            PipelineExecutionListener listener,
            PipelineExecutionRepository repository
    ) {
        List<StepExecutor> executors = List.of(
                new CsvExtractor(),
                new CashValidator(),
                new ColumnMappingTransformer(),
                new UppercaseTransformer(),
                new CsvLoader(),
                new CashFrontCsvLoader(),
                new CashFrontCsvPrepareLoader(),
                new CashOutputPublisherLoader(),
                new SqlServerCashStagingLoader()
        );

        return new PipelineExecutor(executors, listener, repository);
    }
}