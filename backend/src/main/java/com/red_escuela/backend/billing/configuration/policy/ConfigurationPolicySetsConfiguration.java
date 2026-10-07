package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ConfigurationPolicySetsConfiguration {

    @Bean
    public PolicySet<YearBillingConfigurationChange> yearBillingConfigurationPolicySet() {
        List<Policy<YearBillingConfigurationChange>> ordered = List.of(
                new TermHasDatesPolicy(),
                new SecondEnrollmentFeeAfterFirstPolicy(),
                new FirstEnrollmentFeeNotAfterTermStartPolicy(),
                new SecondEnrollmentFeeWithinTermPolicy(),
                new InstallmentCountWithinTermSpanPolicy());
        return PolicySet.named("YEAR_BILLING_CONFIGURATION", ordered);
    }

    @Bean
    public PolicySet<YearBillingConfigurationChange> yearBillingConfigurationFreezePolicySet() {
        List<Policy<YearBillingConfigurationChange>> ordered = List.of(
                new EnrollmentFeeCountFrozenPolicy(),
                new EnrollmentFeeMonthFrozenPolicy(),
                new IssuedInstallmentsWithinTermRangePolicy());
        return PolicySet.named("YEAR_BILLING_CONFIGURATION_FREEZE", ordered);
    }

    @Bean
    public PolicySet<LatePaymentSettingsValues> latePaymentSettingsPolicySet() {
        List<Policy<LatePaymentSettingsValues>> ordered = List.of(
                new GracePeriodNotNegativePolicy(),
                new GracePeriodWithinSuspensionDaysPolicy(),
                new SuspensionInvoiceCountAtLeastOnePolicy(),
                new EffectiveDateNotRetroactivePolicy());
        return PolicySet.named("LATE_PAYMENT_SETTINGS", ordered);
    }
}
