package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.exception.BillingConfigurationFrozenException;
import com.red_escuela.backend.billing.configuration.exception.InvalidLatePaymentSettingsException;
import com.red_escuela.backend.billing.configuration.exception.InvalidYearBillingConfigurationException;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluation;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class ConfigurationPolicyEvaluator {
    private final PolicySet<YearBillingConfigurationChange> yearCoherencePolicies;
    private final PolicySet<YearBillingConfigurationChange> yearFreezePolicies;
    private final PolicySet<LatePaymentSettingsValues> latePaymentPolicies;
    private final MeterRegistry meterRegistry;

    public ConfigurationPolicyEvaluator(PolicySet<YearBillingConfigurationChange> yearBillingConfigurationPolicySet,
                                        PolicySet<YearBillingConfigurationChange> yearBillingConfigurationFreezePolicySet,
                                        PolicySet<LatePaymentSettingsValues> latePaymentSettingsPolicySet,
                                        MeterRegistry meterRegistry) {
        this.yearCoherencePolicies = yearBillingConfigurationPolicySet;
        this.yearFreezePolicies = yearBillingConfigurationFreezePolicySet;
        this.latePaymentPolicies = latePaymentSettingsPolicySet;
        this.meterRegistry = meterRegistry;
    }

    public void requireCoherentYearConfiguration(YearBillingConfigurationChange change) {
        evaluate(yearCoherencePolicies, change, InvalidYearBillingConfigurationException::new);
    }

    public void requireUnfrozenYearConfiguration(YearBillingConfigurationChange change) {
        evaluate(yearFreezePolicies, change, BillingConfigurationFrozenException::new);
    }

    public void requireCoherentLatePaymentSettings(LatePaymentSettingsValues values) {
        evaluate(latePaymentPolicies, values, InvalidLatePaymentSettingsException::new);
    }

    private <C> void evaluate(PolicySet<C> policies, C context, Function<List<PolicyViolation>, ? extends RuntimeException> exceptionFactory) {
        PolicyEvaluation evaluation = policies.evaluateAll(context);
        evaluation.violations().forEach(violation ->
                meterRegistry.counter("billing_configuration_rejected_total", "moment", policies.moment(), "code", violation.code()).increment());
        evaluation.throwIfDenied(exceptionFactory);
    }
}
