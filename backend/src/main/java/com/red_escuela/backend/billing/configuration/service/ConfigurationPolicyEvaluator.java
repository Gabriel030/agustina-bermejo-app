package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.exception.BillingConfigurationFrozenException;
import com.red_escuela.backend.billing.configuration.exception.EffectiveFromBeforeNextDayException;
import com.red_escuela.backend.billing.configuration.exception.InvalidCollectionReminderScheduleException;
import com.red_escuela.backend.billing.configuration.exception.InvalidCollectionReminderSettingsException;
import com.red_escuela.backend.billing.configuration.policy.ReminderScheduleChangeCheck;
import com.red_escuela.backend.billing.configuration.policy.ReminderScheduleCheck;
import com.red_escuela.backend.billing.configuration.policy.CollectionReminderEffectiveFromCheck;
import com.red_escuela.backend.billing.configuration.exception.InvalidLatePaymentSettingsException;
import com.red_escuela.backend.billing.configuration.exception.InvalidYearBillingConfigurationException;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.exception.InvalidAnnualUpfrontSettingsException;
import com.red_escuela.backend.billing.configuration.exception.InvalidIssuanceSettingsException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.ProposalExpiredException;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontCheck;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationChangeCheck;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationViolationExceptions;
import com.red_escuela.backend.billing.configuration.policy.LatePaymentSettingsValues;
import com.red_escuela.backend.billing.configuration.policy.ProposalApprovalCheck;
import com.red_escuela.backend.billing.configuration.policy.ProposalSlot;
import com.red_escuela.backend.billing.configuration.policy.VersionCancellationCheck;
import com.red_escuela.backend.billing.configuration.policy.YearBillingConfigurationChange;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
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
    private final PolicySet<ConfigurationChangeCheck> changePolicies;
    private final PolicySet<ProposalSlot> proposalPolicies;
    private final PolicySet<ProposalApprovalCheck> approvalPolicies;
    private final PolicySet<VersionCancellationCheck> cancellationPolicies;
    private final PolicySet<LatePaymentTerms> lateTermsPolicies;
    private final PolicySet<IssuanceSettings> issuancePolicies;
    private final PolicySet<AnnualUpfrontCheck> annualUpfrontPolicies;
    private final PolicySet<CollectionReminderSettings> collectionReminderSettingsPolicies;
    private final PolicySet<CollectionReminderEffectiveFromCheck> collectionReminderEffectiveFromPolicies;
    private final PolicySet<ReminderScheduleCheck> collectionReminderSchedulePolicies;
    private final PolicySet<ReminderScheduleChangeCheck> collectionReminderScheduleChangePolicies;
    private final MeterRegistry meterRegistry;

    public ConfigurationPolicyEvaluator(PolicySet<YearBillingConfigurationChange> yearBillingConfigurationPolicySet,
                                        PolicySet<YearBillingConfigurationChange> yearBillingConfigurationFreezePolicySet,
                                        PolicySet<LatePaymentSettingsValues> latePaymentSettingsPolicySet,
                                        PolicySet<ConfigurationChangeCheck> billingConfigurationChangePolicySet,
                                        PolicySet<ProposalSlot> billingConfigurationProposalPolicySet,
                                        PolicySet<ProposalApprovalCheck> configurationProposalApprovalPolicySet,
                                        PolicySet<VersionCancellationCheck> configurationVersionCancellationPolicySet,
                                        PolicySet<LatePaymentTerms> latePaymentTermsPolicySet,
                                        PolicySet<IssuanceSettings> issuanceSettingsPolicySet,
                                        PolicySet<AnnualUpfrontCheck> annualUpfrontSettingsPolicySet,
                                        PolicySet<CollectionReminderSettings> collectionReminderSettingsPolicySet,
                                        PolicySet<CollectionReminderEffectiveFromCheck> collectionReminderEffectiveFromPolicySet,
                                        PolicySet<ReminderScheduleCheck> collectionReminderSchedulePolicySet,
                                        PolicySet<ReminderScheduleChangeCheck> collectionReminderScheduleChangePolicySet,
                                        MeterRegistry meterRegistry) {
        this.changePolicies = billingConfigurationChangePolicySet;
        this.proposalPolicies = billingConfigurationProposalPolicySet;
        this.approvalPolicies = configurationProposalApprovalPolicySet;
        this.cancellationPolicies = configurationVersionCancellationPolicySet;
        this.lateTermsPolicies = latePaymentTermsPolicySet;
        this.issuancePolicies = issuanceSettingsPolicySet;
        this.annualUpfrontPolicies = annualUpfrontSettingsPolicySet;
        this.yearCoherencePolicies = yearBillingConfigurationPolicySet;
        this.yearFreezePolicies = yearBillingConfigurationFreezePolicySet;
        this.latePaymentPolicies = latePaymentSettingsPolicySet;
        this.collectionReminderSettingsPolicies = collectionReminderSettingsPolicySet;
        this.collectionReminderEffectiveFromPolicies = collectionReminderEffectiveFromPolicySet;
        this.collectionReminderSchedulePolicies = collectionReminderSchedulePolicySet;
        this.collectionReminderScheduleChangePolicies = collectionReminderScheduleChangePolicySet;
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

    public void requireValidChange(ConfigurationChangeCheck check) {
        evaluate(changePolicies, check, ConfigurationViolationExceptions::forChangeViolations);
    }

    public void requireNoOpenProposal(ProposalSlot slot) {
        evaluate(proposalPolicies, slot, ProposalAlreadyOpenException::new);
    }

    public void requireProposalNotExpired(ProposalApprovalCheck check) {
        evaluate(approvalPolicies, check, ProposalExpiredException::new);
    }

    public void requireCancellableVersion(VersionCancellationCheck check) {
        evaluate(cancellationPolicies, check, ConfigurationViolationExceptions::forCancellationViolations);
    }

    public void requireCoherentLatePaymentTerms(LatePaymentTerms terms) {
        evaluate(lateTermsPolicies, terms, InvalidLatePaymentSettingsException::new);
    }

    public void requireCoherentIssuanceSettings(IssuanceSettings settings) {
        evaluate(issuancePolicies, settings, InvalidIssuanceSettingsException::new);
    }

    public void requireCoherentAnnualUpfrontSettings(AnnualUpfrontCheck check) {
        evaluate(annualUpfrontPolicies, check, InvalidAnnualUpfrontSettingsException::new);
    }

    public void requireCoherentCollectionReminderSettings(CollectionReminderSettings settings) {
        evaluate(collectionReminderSettingsPolicies, settings, InvalidCollectionReminderSettingsException::new);
    }

    public void requireCoherentCollectionReminderSchedule(ReminderScheduleCheck check) {
        evaluate(collectionReminderSchedulePolicies, check, InvalidCollectionReminderScheduleException::new);
    }

    public void requireReminderScheduleChangeNotToFollowGroup(ReminderScheduleChangeCheck check) {
        evaluate(collectionReminderScheduleChangePolicies, check, InvalidCollectionReminderScheduleException::new);
    }

    public void requireCollectionReminderEffectiveFromNextDay(CollectionReminderEffectiveFromCheck check) {
        evaluate(collectionReminderEffectiveFromPolicies, check, EffectiveFromBeforeNextDayException::new);
    }

    private <C> void evaluate(PolicySet<C> policies, C context, Function<List<PolicyViolation>, ? extends RuntimeException> exceptionFactory) {
        PolicyEvaluationResult evaluation = policies.evaluateAllRules(context);
        evaluation.violations().forEach(violation ->
                meterRegistry.counter("billing_configuration_rejected_total", "moment", policies.moment(), "code", violation.code()).increment());
        evaluation.throwIfDenied(exceptionFactory);
    }
}
