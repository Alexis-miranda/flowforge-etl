package com.flowforge.etl.core.execution;

/**
 * Runtime failure raised by a step executor with an explicit operational
 * classification.
 *
 * <p>The classification is descriptive metadata. The pipeline executor
 * records it but does not perform retries or recovery.</p>
 */
public class StepExecutionFailureException extends RuntimeException {

    private final FailureClassification failureClassification;

    public StepExecutionFailureException(
            FailureClassification failureClassification,
            String message) {
        super(message);
        this.failureClassification = requireClassification(failureClassification);
    }

    public StepExecutionFailureException(
            FailureClassification failureClassification,
            String message,
            Throwable cause) {
        super(message, cause);
        this.failureClassification = requireClassification(failureClassification);
    }

    public FailureClassification failureClassification() {
        return failureClassification;
    }

    private static FailureClassification requireClassification(
            FailureClassification failureClassification) {
        if (failureClassification == null) {
            throw new IllegalArgumentException(
                    "Failure classification must not be null");
        }
        return failureClassification;
    }
}
