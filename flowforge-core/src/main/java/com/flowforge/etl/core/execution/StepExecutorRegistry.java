package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineStep;
import com.flowforge.etl.core.domain.StepType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers step executors and resolves the executor required by a pipeline step.
 *
 * <p>The registry is immutable after construction.</p>
 */
final class StepExecutorRegistry {

    private static final String CONNECTOR_PROPERTY = "connector";

    private final Map<StepType, Map<String, StepExecutor>> executors;

    StepExecutorRegistry(List<StepExecutor> stepExecutors) {
        if (stepExecutors == null) {
            throw new IllegalArgumentException("Step executors must not be null");
        }

        Map<StepType, Map<String, StepExecutor>> mutableExecutors = new HashMap<>();
        for (StepExecutor executor : stepExecutors) {
            register(mutableExecutors, executor);
        }

        this.executors = immutableCopyOf(mutableExecutors);
    }

    StepExecutor resolve(PipelineStep step) {
        Map<String, StepExecutor> connectors = executors.get(step.type());
        if (connectors == null || connectors.isEmpty()) {
            throw new IllegalStateException(
                    "No executor registered for step type: " + step.type());
        }

        String connectorKey = step.configuration().get(CONNECTOR_PROPERTY);
        if (connectorKey != null && !connectorKey.isBlank()) {
            StepExecutor executor = connectors.get(connectorKey);
            if (executor == null) {
                throw new IllegalStateException(
                        "No executor registered for step type " + step.type()
                                + " and connector: " + connectorKey);
            }
            return executor;
        }

        if (connectors.size() == 1) {
            return connectors.values().iterator().next();
        }

        throw new IllegalStateException(
                "Multiple executors are registered for step type " + step.type()
                        + ". Specify the '" + CONNECTOR_PROPERTY + "' property.");
    }

    private static void register(
            Map<StepType, Map<String, StepExecutor>> executors,
            StepExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException(
                    "Step executors must not contain null elements");
        }

        StepType type = executor.supportedType();
        if (type == null) {
            throw new IllegalArgumentException(
                    "Executor supported type must not be null");
        }

        String connectorKey = executor.connectorKey();
        if (connectorKey == null || connectorKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Executor connector key must not be blank");
        }

        Map<String, StepExecutor> connectors =
                executors.computeIfAbsent(type, ignored -> new HashMap<>());
        if (connectors.containsKey(connectorKey)) {
            throw new IllegalArgumentException(
                    "Duplicate executor for step type " + type
                            + " and connector: " + connectorKey);
        }

        connectors.put(connectorKey, executor);
    }

    private static Map<StepType, Map<String, StepExecutor>> immutableCopyOf(
            Map<StepType, Map<String, StepExecutor>> executors) {
        Map<StepType, Map<String, StepExecutor>> immutableExecutors = new HashMap<>();
        executors.forEach((type, connectors) ->
                immutableExecutors.put(type, Map.copyOf(connectors)));
        return Map.copyOf(immutableExecutors);
    }
}
