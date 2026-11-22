package com.flowforge.etl.core.execution;

/**
 * Checks whether the current worker is still allowed to execute under its lease.
 * Implementations may be backed by a periodically renewed distributed lease.
 */
@FunctionalInterface
public interface PipelineExecutionLeaseGuard {

    /** Throws when the worker can no longer safely continue this execution. */
    void assertLeaseValid();
}
