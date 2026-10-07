package com.red_escuela.backend.billing.configuration;

import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.configuration.policy.EnrollmentFeeCountFrozenPolicy;
import com.red_escuela.backend.billing.configuration.policy.EnrollmentFeeLeadMonthsFrozenPolicy;
import com.red_escuela.backend.billing.configuration.policy.EnrollmentFeeMonthFrozenPolicy;
import com.red_escuela.backend.billing.configuration.policy.FirstEnrollmentFeeNotAfterTermStartPolicy;
import com.red_escuela.backend.billing.configuration.policy.GracePeriodNotNegativePolicy;
import com.red_escuela.backend.billing.configuration.policy.GracePeriodWithinSuspensionDaysPolicy;
import com.red_escuela.backend.billing.configuration.policy.InstallmentCountWithinTermSpanPolicy;
import com.red_escuela.backend.billing.configuration.policy.IssuedInstallmentsWithinTermRangePolicy;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.configuration.policy.SecondEnrollmentFeeAfterFirstPolicy;
import com.red_escuela.backend.billing.configuration.policy.SecondEnrollmentFeeWithinTermPolicy;
import com.red_escuela.backend.billing.configuration.policy.SuspensionDaysNotNegativePolicy;
import com.red_escuela.backend.billing.configuration.policy.SuspensionInvoiceCountAtLeastOnePolicy;
import com.red_escuela.backend.billing.configuration.policy.TermHasDatesPolicy;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
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
                new EnrollmentFeeLeadMonthsFrozenPolicy(),
                new IssuedInstallmentsWithinTermRangePolicy());
        return PolicySet.named("YEAR_BILLING_CONFIGURATION_FREEZE", ordered);
    }

    @Bean
    public PolicySet<LatePaymentSettingsValues> latePaymentSettingsPolicySet() {
        List<Policy<LatePaymentSettingsValues>> ordered = List.of(
                new GracePeriodNotNegativePolicy(),
                new SuspensionDaysNotNegativePolicy(),
                new GracePeriodWithinSuspensionDaysPolicy(),
                new SuspensionInvoiceCountAtLeastOnePolicy(),
                new EffectiveDateNotRetroactivePolicy());
        return PolicySet.named("LATE_PAYMENT_SETTINGS", ordered);
    }
}
