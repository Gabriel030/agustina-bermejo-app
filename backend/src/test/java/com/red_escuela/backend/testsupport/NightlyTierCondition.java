package com.red_escuela.backend.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class NightlyTierCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (ExplorationTier.current() == ExplorationTier.NIGHTLY) {
            return ConditionEvaluationResult.enabled("tier nightly");
        }
        return ConditionEvaluationResult.disabled("runs only with -D" + ExplorationTier.PROPERTY + "=nightly");
    }
}
