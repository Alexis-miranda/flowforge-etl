package com.flowforge.etl.cash.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.execution.PipelineExecutor;
import com.flowforge.etl.messaging.consumer.NonRetryablePipelineExecutionRequestException;
import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestPipeline;
import com.flowforge.etl.messaging.consumer.PipelineExecutionRequestPipelineResolver;
import com.flowforge.etl.messaging.request.PipelineExecutionRequest;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Resolves asynchronous Cash requests into the existing Cash ETL pipeline.
 *
 * <p>The supplied executor must use the same durable execution repository as
 * the lease-protected request handler.</p>
 */
public final class CashPipelineExecutionRequestResolver
        implements PipelineExecutionRequestPipelineResolver {

    public static final String PIPELINE_TYPE = "cash";
    public static final String PIPELINE_ID = "cash-etl";

    private static final String DEFAULT_COLUMNS = "accountCode,currency,amount";
    private static final Set<String> ALLOWED_PARAMETERS =
            Set.of("input", "backOfficeCode", "outputDirectory", "columns", "sourceFile");

    private final PipelineExecutor executor;

    public CashPipelineExecutionRequestResolver(PipelineExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Pipeline executor must not be null");
        }
        this.executor = executor;
    }

    @Override
    public PipelineExecutionRequestPipeline resolve(PipelineExecutionRequest request) {
        if (request == null) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Pipeline execution request must not be null");
        }
        if (!PIPELINE_TYPE.equals(request.pipelineType())) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Unsupported pipeline type: " + request.pipelineType());
        }

        Set<String> unknownParameters = request.parameters().keySet().stream()
                .filter(key -> !ALLOWED_PARAMETERS.contains(key))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!unknownParameters.isEmpty()) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Unsupported Cash request parameters: " + unknownParameters);
        }

        Path input = requiredPath(request, "input");
        Path outputDirectory = requiredPath(request, "outputDirectory");
        String backOfficeCode = requiredText(request, "backOfficeCode");
        String columns = request.parameters().getOrDefault("columns", DEFAULT_COLUMNS);
        String sourceFile = request.parameters().getOrDefault(
                "sourceFile", input.getFileName().toString());

        if (!Files.isRegularFile(input) || !Files.isReadable(input)) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Cash input file does not exist or is not readable: " + input);
        }
        if (!backOfficeCode.matches("[A-Za-z0-9_-]+")) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "backOfficeCode may contain only letters, digits, '_' and '-'.");
        }
        if (columns == null || columns.isBlank()) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Cash output columns must not be blank");
        }
        if (sourceFile == null || sourceFile.isBlank() || sourceFile.length() > 260) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "sourceFile must contain 1 to 260 characters.");
        }

        PipelineDefinition definition = CashPipelineDefinitionFactory.create(
                PIPELINE_ID, input, backOfficeCode, outputDirectory, columns, sourceFile);
        return new PipelineExecutionRequestPipeline(definition, executor);
    }

    private static Path requiredPath(PipelineExecutionRequest request, String key) {
        String value = requiredText(request, key);
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Invalid path for Cash parameter '" + key + "'", exception);
        }
    }

    private static String requiredText(PipelineExecutionRequest request, String key) {
        String value = request.parameters().get(key);
        if (value == null || value.isBlank()) {
            throw new NonRetryablePipelineExecutionRequestException(
                    "Missing required Cash request parameter: " + key);
        }
        return value.trim();
    }
}
