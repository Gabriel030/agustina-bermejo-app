package com.red_escuela.backend.billing.configuration;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderEffectiveFromCheck;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderEffectiveFromNextDayPolicy;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderSettingsCoherencePolicy;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontCheck;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontSettingsCoherencePolicy;
import com.red_escuela.backend.billing.configuration.policy.CancellationReasonRequiredPolicy;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationChangeCheck;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationChangeReasonRequiredPolicy;
import com.red_escuela.backend.billing.configuration.policy.EffectiveDateNotRetroactivePolicy;
import com.red_escuela.backend.billing.configuration.policy.IssuanceSettingsCoherencePolicy;
import com.red_escuela.backend.billing.configuration.policy.LateFeeSettingsCoherencePolicy;
import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.configuration.policy.ProposalNotExpiredPolicy;
import com.red_escuela.backend.billing.configuration.policy.ProposalSlot;
import com.red_escuela.backend.billing.configuration.policy.ScheduledVersionCancellablePolicy;
import com.red_escuela.backend.billing.configuration.policy.SingleOpenProposalPerTopicPolicy;
import com.red_escuela.backend.billing.configuration.policy.VersionCancellationCheck;
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
                new EffectiveDateNotRetroactivePolicy<>());
        return PolicySet.named("LATE_PAYMENT_SETTINGS", ordered);
    }

    @Bean
    public PolicySet<ConfigurationChangeCheck> billingConfigurationChangePolicySet() {
        List<Policy<ConfigurationChangeCheck>> ordered = List.of(
                new ConfigurationChangeReasonRequiredPolicy(),
                new EffectiveDateNotRetroactivePolicy<>());
        return PolicySet.named("BILLING_CONFIGURATION_CHANGE", ordered);
    }

    @Bean
    public PolicySet<ProposalSlot> billingConfigurationProposalPolicySet() {
        List<Policy<ProposalSlot>> ordered = List.of(new SingleOpenProposalPerTopicPolicy());
        return PolicySet.named("BILLING_CONFIGURATION_PROPOSAL", ordered);
    }

    @Bean
    public PolicySet<ProposalApprovalCheck> configurationProposalApprovalPolicySet() {
        List<Policy<ProposalApprovalCheck>> ordered = List.of(new ProposalNotExpiredPolicy());
        return PolicySet.named("CONFIGURATION_PROPOSAL_APPROVAL", ordered);
    }

    @Bean
    public PolicySet<VersionCancellationCheck> configurationVersionCancellationPolicySet() {
        List<Policy<VersionCancellationCheck>> ordered = List.of(
                new CancellationReasonRequiredPolicy(),
                new ScheduledVersionCancellablePolicy());
        return PolicySet.named("CONFIGURATION_VERSION_CANCELLATION", ordered);
    }

    @Bean
    public PolicySet<LatePaymentTerms> latePaymentTermsPolicySet() {
        List<Policy<LatePaymentTerms>> ordered = List.of(new LateFeeSettingsCoherencePolicy());
        return PolicySet.named("LATE_PAYMENT_TERMS", ordered);
    }

    @Bean
    public PolicySet<IssuanceSettings> issuanceSettingsPolicySet() {
        List<Policy<IssuanceSettings>> ordered = List.of(new IssuanceSettingsCoherencePolicy());
        return PolicySet.named("ISSUANCE_SETTINGS", ordered);
    }

    @Bean
    public PolicySet<AnnualUpfrontCheck> annualUpfrontSettingsPolicySet() {
        List<Policy<AnnualUpfrontCheck>> ordered = List.of(new AnnualUpfrontSettingsCoherencePolicy());
        return PolicySet.named("ANNUAL_UPFRONT_SETTINGS", ordered);
    }

    @Bean
    public PolicySet<CollectionReminderSettings> collectionReminderSettingsPolicySet() {
        List<Policy<CollectionReminderSettings>> ordered = List.of(new CollectionReminderSettingsCoherencePolicy());
        return PolicySet.named("COLLECTION_REMINDER_SETTINGS", ordered);
    }

    @Bean
    public PolicySet<CollectionReminderEffectiveFromCheck> collectionReminderEffectiveFromPolicySet() {
        List<Policy<CollectionReminderEffectiveFromCheck>> ordered = List.of(new CollectionReminderEffectiveFromNextDayPolicy());
        return PolicySet.named("COLLECTION_REMINDER_EFFECTIVE_FROM", ordered);
    }
}
