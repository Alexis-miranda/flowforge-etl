package com.flowforge.etl.core.execution;

import com.flowforge.etl.core.domain.PipelineDefinition;
import com.flowforge.etl.core.domain.PipelineStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces conservative recovery recommendations from persisted step state
 * and caller-supplied step policies. It never acquires a lease or retries work.
 */
public final class PipelineStepRecoveryEvaluator {

    /**
     * Assesses every step in pipeline order. Missing and SKIPPED rows are
     * reported as NOT_STARTED. Unknown policies default to UNCLASSIFIED.
     */
    public List<StepRecoveryAssessment> assess(
            PipelineDefinition pipeline,
            List<PersistedStepExecution> persistedSteps,
            Map<String, StepRecoveryPolicy> policies) {
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline must not be null");
        }
        if (persistedSteps == null) {
            throw new IllegalArgumentException("Persisted steps must not be null");
        }
        if (policies == null) {
            throw new IllegalArgumentException("Recovery policies must not be null");
        }

        Map<String, PersistedStepExecution> byStepId = new HashMap<>();
        for (PersistedStepExecution persisted : persistedSteps) {
            if (persisted == null) {
                throw new IllegalArgumentException("Persisted steps must not contain null");
            }
            if (byStepId.putIfAbsent(persisted.stepId(), persisted) != null) {
                throw new IllegalArgumentException(
                        "Duplicate persisted step record: " + persisted.stepId());
            }
        }

        Map<String, Boolean> pipelineStepIds = new HashMap<>();
        for (PipelineStep step : pipeline.steps()) {
            pipelineStepIds.put(step.id(), Boolean.TRUE);
        }
        for (String persistedStepId : byStepId.keySet()) {
            if (!pipelineStepIds.containsKey(persistedStepId)) {
                throw new IllegalArgumentException(
                        "Persisted step does not belong to pipeline: " + persistedStepId);
            }
        }
        for (String policyStepId : policies.keySet()) {
            if (policyStepId == null || !pipelineStepIds.containsKey(policyStepId)) {
                throw new IllegalArgumentException(
                        "Recovery policy references an unknown pipeline step: " + policyStepId);
            }
        }

        List<StepRecoveryAssessment> assessments = new ArrayList<>();
        for (PipelineStep step : pipeline.steps()) {
            StepRecoveryPolicy policy = policies.getOrDefault(
                    step.id(), StepRecoveryPolicy.UNCLASSIFIED);
            if (policy == null) {
                throw new IllegalArgumentException(
                        "Recovery policy must not be null for step: " + step.id());
            }

            PersistedStepExecution persisted = byStepId.get(step.id());
            if (persisted == null || persisted.status() == PersistedStepExecutionStatus.SKIPPED) {
                assessments.add(new StepRecoveryAssessment(
                        step.id(),
                        PersistedStepExecutionStatus.SKIPPED,
                        policy,
                        StepRecoveryAction.NOT_STARTED,
                        "The step has no completed execution record and has not run in this execution"));
                continue;
            }

            assessments.add(assess(step.id(), persisted, policy));
        }
        return List.copyOf(assessments);
    }

    private StepRecoveryAssessment assess(
            String stepId,
            PersistedStepExecution persisted,
            StepRecoveryPolicy policy) {
        return switch (persisted.status()) {
            case SUCCEEDED -> new StepRecoveryAssessment(
                    stepId, persisted.status(), policy,
                    StepRecoveryAction.ALREADY_SUCCEEDED,
                    "The step has a durable successful completion record");
            case RUNNING -> new StepRecoveryAssessment(
                    stepId, persisted.status(), policy,
                    StepRecoveryAction.RECONCILIATION_REQUIRED,
                    "The worker may have performed an external side effect before interruption");
            case FAILED -> assessFailure(stepId, persisted, policy);
            case SKIPPED -> throw new IllegalStateException("Skipped steps are assessed as not started");
        };
    }

    private StepRecoveryAssessment assessFailure(
            String stepId,
            PersistedStepExecution persisted,
            StepRecoveryPolicy policy) {
        if (policy == StepRecoveryPolicy.MANUAL_ONLY) {
            return new StepRecoveryAssessment(
                    stepId, persisted.status(), policy,
                    StepRecoveryAction.MANUAL_INTERVENTION,
                    "The step policy requires an explicit operator decision");
        }
        if (policy == StepRecoveryPolicy.IDEMPOTENT
                && persisted.failureClassification() == FailureClassification.RETRYABLE) {
            return new StepRecoveryAssessment(
                    stepId, persisted.status(), policy,
                    StepRecoveryAction.ELIGIBLE_FOR_RETRY,
                    "The step is declared idempotent and its failure is classified as retryable; a current lease is still required");
        }
        return new StepRecoveryAssessment(
                stepId, persisted.status(), policy,
                StepRecoveryAction.RECONCILIATION_REQUIRED,
                "The available failure and policy metadata do not prove that repeating the step is safe");
    }
}
