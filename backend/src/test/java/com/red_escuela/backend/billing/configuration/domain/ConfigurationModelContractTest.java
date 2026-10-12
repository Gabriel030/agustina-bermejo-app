package com.red_escuela.backend.billing.configuration.domain;

import com.red_escuela.backend.billing.configuration.exception.CancellationReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ChangeReasonRequiredException;
import com.red_escuela.backend.billing.configuration.exception.ProposalAlreadyOpenException;
import com.red_escuela.backend.billing.configuration.exception.RetroactiveEffectiveDateException;
import com.red_escuela.backend.billing.configuration.exception.VersionAlreadyEffectiveException;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.policy.ConfigurationViolationExceptions;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-095")
class ConfigurationModelContractTest {
    private static final Instant NOW = Instant.parse("2027-11-15T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 11, 15);

    @Test
    void everyPolicyOfTheConfigurationPackageNamesItselfWithItsClassName() throws Exception {
        Path directory = Path.of("target/classes/com/red_escuela/backend/billing/configuration/policy");
        try (Stream<Path> files = Files.list(directory)) {
            List<String> names = files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith("Policy.class")).map(name -> name.replace(".class", "")).toList();
            assertThat(names).hasSizeGreaterThan(15);
            for (String name : names) {
                Class<?> type = Class.forName("com.red_escuela.backend.billing.configuration.policy." + name);
                try {
                    Policy<?> policy = (Policy<?>) type.getDeclaredConstructor().newInstance();
                    assertThat(policy.ruleName()).isEqualTo(name);
                    assertThat(type.getAnnotation(PolicyRule.class).violationCodes()).isNotEmpty();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(name, e);
                }
            }
        }
    }

    @Test
    void everyValuesTypeReportsItsOwnScopeAndRoundTripsThroughJson() {
        List<ConfigurationValues> samples = List.of(
                new YearBillingTerms(10, 2, 2, 7, 1, 10, 10, true, false, true, true, true, false),
                new CohortEntryDiscount(Percentage.ofPoints(new BigDecimal("15"))),
                new LatePaymentTerms(0, Percentage.ofPoints(new BigDecimal("0.20")), 30, 2, true, 10, true, true),
                new IssuanceSettings(IssuanceMode.REVIEW, true, 2, List.of(1, 3)),
                new AllocationSettings(true),
                new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 48),
                new CourseTerms(10),
                new IrregularTermTypes(List.of("Intensivo", "Verano")),
                CollectionReminderSettings.initial(),
                new BillingDashboardGoals(Percentage.ofPoints(new BigDecimal("85")), null, 3, null));
        assertThat(samples.stream().map(ConfigurationValues::scope)).containsExactly(ConfigurationScope.values());
        for (ConfigurationValues sample : samples) {
            assertThat(ConfigurationValuesCodec.fromJson(ConfigurationValuesCodec.toJson(sample))).isEqualTo(sample);
        }
    }

    @Test
    @Tag("D-351")
    void collectionReminderSettingsUseTheirOwnJsonTypeGlobalTopicAndRequireTheEmailWindow() {
        String json = ConfigurationValuesCodec.toJson(CollectionReminderSettings.initial());

        assertThat(json).contains("\"type\":\"COLLECTION_REMINDERS\"", "\"emailWindowStart\":\"09:00:00\"", "\"dailyEmailCap\":250");
        assertThat(ConfigurationValuesCodec.fromJson("{\"type\":\"COLLECTION_REMINDERS\",\"emissionReminderEnabled\":true,\"daysAfterIssueReminder\":5,"
                + "\"daysBeforeDueReminder\":1,\"daysAfterDueReminder\":10,\"daysBeforeSuspensionReminder\":1,\"suspensionNoticeEnabled\":true,"
                + "\"postWithdrawalSummaryMonths\":6,\"minDaysBetweenEmails\":3,\"maxReminderEmailsPerMonth\":4,\"emailWindowStart\":\"09:00:00\","
                + "\"emailWindowEnd\":\"11:00:00\",\"dailyEmailCap\":250,\"contactListDaysOverdue\":14,\"contactListOverdueInstallments\":2}"))
                .isEqualTo(CollectionReminderSettings.initial());
        assertThat(ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS).key()).isEqualTo("COLLECTION_REMINDERS");
        assertThatThrownBy(() -> new ConfigurationTopic(ConfigurationScope.COLLECTION_REMINDERS, 1L, null, null, null)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new CollectionReminderSettings(true, 5, 1, 10, 1, true, 6, 3, 4, null, java.time.LocalTime.of(11, 0), 250, 14, 2))
                .isInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Test
    void irregularTermTypesAreTrimmedDeduplicatedSortedAndNeverBlank() {
        assertThat(new IrregularTermTypes(List.of(" Verano", "Intensivo", "Verano ")).types()).containsExactly("Intensivo", "Verano");
        assertThatThrownBy(() -> new IrregularTermTypes(List.of("a", " "))).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new IrregularTermTypes(java.util.Arrays.asList("a", null))).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new IrregularTermTypes(null)).isInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Test
    void topicsBuildTheirKeysAndRejectForeignCoordinates() {
        assertThat(ConfigurationTopic.ofYearTerms(12L, 40L).key()).isEqualTo("YEAR_TERMS:P12:T40");
        assertThat(ConfigurationTopic.ofAnnualUpfront(12L, 40L).key()).isEqualTo("ANNUAL_UPFRONT:P12:T40");
        assertThat(ConfigurationTopic.ofCohortEntry(7L).key()).isEqualTo("COHORT_ENTRY:C7");
        assertThat(ConfigurationTopic.ofCourseTerms(9L).key()).isEqualTo("COURSE_TERMS:C9");
        assertThat(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT).key()).isEqualTo("LATE_PAYMENT");
        assertThat(ConfigurationTopic.ofGlobal(ConfigurationScope.ISSUANCE).scope()).isEqualTo(ConfigurationScope.ISSUANCE);
        assertThat(ConfigurationTopic.ofCohortEntry(7L).cohortId()).isEqualTo(7L);
        assertThatThrownBy(() -> new ConfigurationTopic(ConfigurationScope.LATE_PAYMENT, 1L, null, null, null)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new ConfigurationTopic(ConfigurationScope.YEAR_TERMS, 1L, null, null, null)).isInstanceOf(InvalidConfigurationValuesException.class);
        assertThatThrownBy(() -> new ConfigurationTopic(ConfigurationScope.COURSE_TERMS, 1L, null, 2L, null)).isInstanceOf(InvalidConfigurationValuesException.class);
    }

    @Test
    void openProposalAndReachStartFollowTheStatus() {
        ConfigurationVersion proposed = ConfigurationVersion.propose(ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION), new AllocationSettings(true), TODAY.plusDays(5), "m", 1L, 3L, null, NOW);
        assertThat(proposed.isOpenProposal()).isTrue();
        proposed.approve(2L, "ok", TODAY, NOW);
        assertThat(proposed.isOpenProposal()).isFalse();
        proposed.reachStart(NOW);
        assertThat(proposed.getStatus()).isEqualTo(ConfigurationVersionStatus.EFFECTIVE);
        assertThat(proposed.getStatusChanges().get(2).getActorProcess()).isEqualTo("ConfigurationVersionActivation");
    }

    @Test
    void commandBuildsTheAuthorizationReasonAndExpiresOnTheStartDate() {
        ChangeBillingConfigurationCommand command = new ChangeBillingConfigurationCommand(ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION),
                new AllocationSettings(true), TODAY.plusDays(16), "aumento", 5L, false);
        assertThat(command.authorizationExpiresOn()).isEqualTo(TODAY.plusDays(16));
        assertThat(command.authorizationReason()).isEqualTo("Cambio de configuración de facturación (ALLOCATION) con vigencia desde 2027-12-01: aumento");
    }

    @Test
    void violationExceptionsPickTheirTypeFromTheFirstViolationCode() {
        PolicyViolation reason = new PolicyViolation("CHANGE_REASON_REQUIRED", "x");
        PolicyViolation date = new PolicyViolation("RETROACTIVE_EFFECTIVE_DATE", "x");
        PolicyViolation cancelReason = new PolicyViolation("CANCELLATION_REASON_REQUIRED", "x");
        PolicyViolation effective = new PolicyViolation("VERSION_ALREADY_EFFECTIVE", "x");
        assertThat(ConfigurationViolationExceptions.forChangeViolations(List.of(reason))).isExactlyInstanceOf(ChangeReasonRequiredException.class);
        assertThat(ConfigurationViolationExceptions.forChangeViolations(List.of(date))).isExactlyInstanceOf(RetroactiveEffectiveDateException.class);
        assertThat(ConfigurationViolationExceptions.forCancellationViolations(List.of(cancelReason))).isExactlyInstanceOf(CancellationReasonRequiredException.class);
        assertThat(ConfigurationViolationExceptions.forCancellationViolations(List.of(effective))).isExactlyInstanceOf(VersionAlreadyEffectiveException.class);
        assertThat(ConfigurationViolationExceptions.proposalAlreadyOpen()).isExactlyInstanceOf(ProposalAlreadyOpenException.class);
    }
}
