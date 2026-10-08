package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationVersionStatus;
import com.red_escuela.backend.billing.configuration.domain.InvalidConfigurationValuesException;
import com.red_escuela.backend.billing.configuration.domain.IssuanceMode;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationPoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);
    private static final Percentage FIVE = Percentage.ofPoints(new BigDecimal("5"));

    private static LatePaymentTerms late(int grace, Integer capMonths, Percentage capPct, int suspendDays, int invoices, int ndDays) {
        return new LatePaymentTerms(grace, FIVE, capMonths, capPct, suspendDays, invoices, true, ndDays, true);
    }

    private static IssuanceSettings issuance(int attempts, List<Integer> backoff) {
        return new IssuanceSettings(IssuanceMode.REVIEW, true, attempts, backoff);
    }

    @Tag("D-111")
    @ParameterizedTest(name = "inicio {0} con hoy {1} permitido={2}")
    @CsvSource({"2027-11-14,2027-11-15,false", "2027-11-15,2027-11-15,true", "2027-11-16,2027-11-15,true", "2028-02-29,2028-02-29,true", "2028-02-28,2028-02-29,false"})
    void startDateNeverInThePast(LocalDate start, LocalDate today, boolean allowed) {
        PolicyVerdict verdict = new EffectiveDateNotRetroactivePolicy<ConfigurationChangeCheck>().evaluateRule(new ConfigurationChangeCheck(ConfigurationScope.ALLOCATION, "k", start, today, "r"));
        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(verdict.code()).isEqualTo(allowed ? null : "RETROACTIVE_EFFECTIVE_DATE");
    }

    @Tag("D-095")
    @ParameterizedTest
    @CsvSource(value = {"motivo,true", "' ',false", ",false"})
    void changeReasonIsRequired(String reason, boolean allowed) {
        PolicyVerdict verdict = new ConfigurationChangeReasonRequiredPolicy().evaluateRule(new ConfigurationChangeCheck(ConfigurationScope.ALLOCATION, "k", TODAY, TODAY, reason));
        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(verdict.code()).isEqualTo(allowed ? null : "CHANGE_REASON_REQUIRED");
    }

    @Tag("D-116")
    @Test
    void secondOpenProposalOnTheSameTopicIsDenied() {
        SingleOpenProposalPerTopicPolicy policy = new SingleOpenProposalPerTopicPolicy();
        assertThat(policy.evaluateRule(new ProposalSlot("LATE_PAYMENT", false)).allowed()).isTrue();
        PolicyVerdict denied = policy.evaluateRule(new ProposalSlot("LATE_PAYMENT", true));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.code()).isEqualTo("PROPOSAL_ALREADY_OPEN");
    }

    @Tag("D-180")
    @ParameterizedTest(name = "inicio {0}, hoy {1}, propuesta del {2} permitido={3}")
    @CsvSource({"2027-12-01,2027-11-30,2027-11-01,true", "2027-12-01,2027-12-01,2027-11-01,false", "2027-12-01,2027-12-01,2027-12-01,true",
            "2027-12-01,2027-12-02,2027-12-01,false", "2027-12-01,2027-12-02,2027-12-02,true"})
    void proposalThatReachedItsStartWithoutDecisionCannotBeApproved(LocalDate start, LocalDate today, LocalDate proposedOn, boolean allowed) {
        PolicyVerdict verdict = new ProposalNotExpiredPolicy().evaluateRule(new ProposalApprovalCheck(start, today, proposedOn));
        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(verdict.code()).isEqualTo(allowed ? null : "PROPOSAL_EXPIRED");
    }

    @Tag("D-115")
    @Test
    void onlyAScheduledVersionThatHasNotStartedCanBeCancelledWithAReason() {
        ScheduledVersionCancellablePolicy scheduled = new ScheduledVersionCancellablePolicy();
        LocalDate start = LocalDate.of(2027, 12, 1);
        assertThat(scheduled.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.SCHEDULED, start, LocalDate.of(2027, 11, 15), "m")).allowed()).isTrue();
        assertThat(scheduled.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.SCHEDULED, start, start, "m")).code()).isEqualTo("VERSION_ALREADY_EFFECTIVE");
        assertThat(scheduled.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.EFFECTIVE, start, LocalDate.of(2027, 11, 15), "m")).allowed()).isFalse();
        assertThat(scheduled.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.PROPOSED, start, LocalDate.of(2027, 11, 15), "m")).allowed()).isFalse();
        CancellationReasonRequiredPolicy reason = new CancellationReasonRequiredPolicy();
        assertThat(reason.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.SCHEDULED, start, TODAY, "m")).allowed()).isTrue();
        assertThat(reason.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.SCHEDULED, start, TODAY, " ")).code()).isEqualTo("CANCELLATION_REASON_REQUIRED");
        assertThat(reason.evaluateRule(new VersionCancellationCheck(ConfigurationVersionStatus.SCHEDULED, start, TODAY, null)).allowed()).isFalse();
    }

    @Tag("D-061")
    @Tag("D-147")
    @Tag("D-149")
    @ParameterizedTest(name = "gracia {0} tope {1} dias {2} facturas {3} ND {4} permitido={5}")
    @CsvSource({"0,12,30,1,10,true", "-1,12,30,1,10,false", "31,12,30,1,10,false", "30,12,30,1,10,true", "0,0,30,1,10,false", "0,1,30,1,10,true",
            "0,12,-1,1,10,false", "0,12,30,0,10,false", "0,12,30,1,-1,false", "0,12,30,1,0,true", "0,12,0,1,10,true"})
    void lateFeeSettingsCoherence(int grace, int capMonths, int suspendDays, int invoices, int ndDays, boolean allowed) {
        PolicyVerdict verdict = new LateFeeSettingsCoherencePolicy().evaluateRule(late(grace, capMonths, null, suspendDays, invoices, ndDays));
        assertThat(verdict.allowed()).isEqualTo(allowed);
        assertThat(verdict.code()).isEqualTo(allowed ? null : "INVALID_LATE_PAYMENT_SETTINGS");
    }

    @Tag("D-061")
    @Test
    void percentageCapMustBePositive() {
        LateFeeSettingsCoherencePolicy policy = new LateFeeSettingsCoherencePolicy();
        assertThat(policy.evaluateRule(late(0, null, Percentage.ofPoints(new BigDecimal("50")), 30, 1, 10)).allowed()).isTrue();
        assertThat(policy.evaluateRule(late(0, null, Percentage.ofPoints(BigDecimal.ZERO), 30, 1, 10)).allowed()).isFalse();
    }

    @Tag("D-061")
    @Test
    void capIsMonthsXorPercentageAndTheRateIsMandatory() {
        assertThatThrownBy(() -> late(0, 0, Percentage.ofPoints(BigDecimal.ZERO), 30, 1, 10)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> late(0, null, null, 30, 1, 10)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new LatePaymentTerms(0, null, 12, null, 30, 1, true, 10, true)).isInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Tag("D-053")
    @Tag("D-160")
    @Test
    void issuanceBackoffMustMatchAttemptsAndGrow() {
        IssuanceSettingsCoherencePolicy policy = new IssuanceSettingsCoherencePolicy();
        assertThat(policy.evaluateRule(issuance(5, List.of(1, 3, 6, 12, 24))).allowed()).isTrue();
        assertThat(policy.evaluateRule(issuance(0, List.of())).allowed()).isTrue();
        assertThat(policy.evaluateRule(issuance(-1, List.of())).code()).isEqualTo("INVALID_ISSUANCE_SETTINGS");
        assertThat(policy.evaluateRule(issuance(3, List.of(1, 3))).allowed()).isFalse();
        assertThat(policy.evaluateRule(issuance(2, List.of(3, 3))).allowed()).isFalse();
        assertThat(policy.evaluateRule(issuance(2, List.of(3, 1))).allowed()).isFalse();
        assertThat(policy.evaluateRule(issuance(1, List.of(0))).allowed()).isFalse();
        assertThatThrownBy(() -> new IssuanceSettings(IssuanceMode.REVIEW, null, 0, List.of())).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new IssuanceSettings(null, true, 0, List.of())).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new IssuanceSettings(IssuanceMode.DIRECT, true, 0, null)).isInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Tag("D-155")
    @Tag("D-156")
    @Test
    void annualUpfrontDeadlineMustPrecedeTheFirstInstallmentAndStayInTheTermYear() {
        AnnualUpfrontSettingsCoherencePolicy policy = new AnnualUpfrontSettingsCoherencePolicy();
        LocalDate termStart = LocalDate.of(2027, 3, 1);
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 48), termStart)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(termStart, 48), termStart)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(termStart.plusDays(1), 48), termStart)).code()).isEqualTo("INVALID_ANNUAL_UPFRONT_SETTINGS");
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2026, 12, 31), 48), termStart)).allowed()).isTrue();
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2025, 12, 31), 48), termStart)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 0), termStart)).allowed()).isFalse();
        assertThat(policy.evaluateRule(new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 1), termStart)).allowed()).isTrue();
    }
}
