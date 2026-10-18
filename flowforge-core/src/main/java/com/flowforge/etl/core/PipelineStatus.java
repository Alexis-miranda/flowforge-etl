
package com.flowforge.etl.core;

/**
 * Represents the lifecycle status of an ETL pipeline execution.
 */
public enum PipelineStatus {

    /** The pipeline has been created but has not started. */
    CREATED,

    /** The pipeline is currently executing. */
    RUNNING,

    /** The pipeline completed successfully. */
    SUCCEEDED,

    /** The pipeline execution failed. */
    FAILED
}
