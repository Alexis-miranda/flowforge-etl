
package com.flowforge.etl.core.domain;

/**
 * Defines the supported categories of ETL pipeline steps.
 */
public enum StepType {

    /** Reads data from a source system. */
    EXTRACT,

    /** Transforms or validates extracted data. */
    TRANSFORM,

    /** Writes processed data to a target system. */
    LOAD
}
