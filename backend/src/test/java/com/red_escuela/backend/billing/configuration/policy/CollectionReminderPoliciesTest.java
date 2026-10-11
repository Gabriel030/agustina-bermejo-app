package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderPoliciesTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 3);
    private final CollectionReminderSettingsCoherencePolicy coherence = new CollectionReminderSettingsCoherencePolicy();
    private final CollectionReminderEffectiveFromNextDayPolicy nextDay = new CollectionReminderEffectiveFromNextDayPolicy();

    private static CollectionReminderSettings settings(int afterIssue, int beforeDue, int afterDue, int beforeSuspension, int months, int spacing, int monthlyMax,
                                                       LocalTime start, LocalTime end, int cap, int contactDays, int contactInstallments) {
        return new CollectionReminderSettings(true, afterIssue, beforeDue, afterDue, beforeSuspension, true, months, spacing, monthlyMax, start, end, cap,
                contactDays, contactInstallments);
    }

    @Test
    @Tag("D-351")
    @Tag("D-359")
    void initialSettingsAreCoherentAndMatchTheDeclaredDefaults() {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();

        assertThat(coherence.evaluateRule(initial).allowed()).isTrue();
        assertThat(initial).isEqualTo(new CollectionReminderSettings(true, 5, 1, 10, 1, true, 6, 3, 4, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2));
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-351")
    @CsvSource({
            "daysBeforeDueReminder 0,5,0,10,1,6,3,4,09:00,11:00,250,14,2",
            "daysBeforeDueReminder 61,5,61,10,1,6,3,4,09:00,11:00,250,14,2",
            "daysAfterIssueReminder 0,0,1,10,1,6,3,4,09:00,11:00,250,14,2",
            "daysAfterIssueReminder 61,61,1,10,1,6,3,4,09:00,11:00,250,14,2",
            "daysAfterDueReminder 0,5,1,0,1,6,3,4,09:00,11:00,250,14,2",
            "daysAfterDueReminder 61,5,1,61,1,6,3,4,09:00,11:00,250,14,2",
            "daysBeforeSuspensionReminder 0,5,1,10,0,6,3,4,09:00,11:00,250,14,2",
            "daysBeforeSuspensionReminder 61,5,1,10,61,6,3,4,09:00,11:00,250,14,2",
            "window inverted,5,1,10,1,6,3,4,11:00,09:00,250,14,2",
            "window empty,5,1,10,1,6,3,4,09:00,09:00,250,14,2",
            "cap 0,5,1,10,1,6,3,4,09:00,11:00,0,14,2",
            "spacing 0,5,1,10,1,6,0,4,09:00,11:00,250,14,2",
            "monthly max 0,5,1,10,1,6,3,0,09:00,11:00,250,14,2",
            "negative months,5,1,10,1,-1,3,4,09:00,11:00,250,14,2",
            "contact days 0,5,1,10,1,6,3,4,09:00,11:00,250,0,2",
            "contact installments 0,5,1,10,1,6,3,4,09:00,11:00,250,14,0"})
    void incoherentSettingsAreDeniedWithTheDeclaredCode(String label, int afterIssue, int beforeDue, int afterDue, int beforeSuspension, int months,
                                                        int spacing, int monthlyMax, LocalTime start, LocalTime end, int cap, int contactDays,
                                                        int contactInstallments) {
        PolicyVerdict verdict = coherence.evaluateRule(settings(afterIssue, beforeDue, afterDue, beforeSuspension, months, spacing, monthlyMax, start, end,
                cap, contactDays, contactInstallments));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("INVALID_COLLECTION_REMINDER_SETTINGS");
        assertThat(CollectionReminderSettingsCoherencePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(verdict.code());
        assertThat(verdict.reason()).isNotBlank();
        if (label.startsWith("days")) {
            assertThat(verdict.reason()).startsWith(label.substring(0, label.indexOf(' ')));
        }
    }

    @ParameterizedTest(name = "dias {0} y {1}: permitido")
    @Tag("D-351")
    @CsvSource({"1,1", "60,60", "1,60"})
    void dayBoundsAreInclusive(int low, int high) {
        assertThat(coherence.evaluateRule(settings(low, high, low, high, 0, 1, 1, LocalTime.of(9, 0), LocalTime.of(9, 1), 1, 1, 1)).allowed()).isTrue();
    }

    @ParameterizedTest(name = "vigencia {0} con hoy {1} permitida={2}")
    @Tag("D-350")
    @CsvSource({"2027-03-03,2027-03-03,false", "2027-03-02,2027-03-03,false", "2027-03-04,2027-03-03,true", "2027-03-01,2027-02-28,true", "2027-02-28,2027-02-28,false"})
    void effectiveFromMustBeAtLeastTheNextDay(LocalDate effectiveFrom, LocalDate today, boolean allowed) {
        PolicyVerdict verdict = nextDay.evaluateRule(new CollectionReminderEffectiveFromCheck(effectiveFrom, today));

        assertThat(verdict.allowed()).isEqualTo(allowed);
        if (!allowed) {
            assertThat(verdict.code()).isEqualTo("EFFECTIVE_FROM_BEFORE_NEXT_DAY");
            assertThat(CollectionReminderEffectiveFromNextDayPolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(verdict.code());
            assertThat(verdict.reason()).isNotBlank();
        }
    }

    @Test
    @Tag("D-350")
    void sameDayVigencyIsRejectedEvenWhenItIsToday() {
        assertThat(nextDay.evaluateRule(new CollectionReminderEffectiveFromCheck(TODAY, TODAY)).allowed()).isFalse();
        assertThat(nextDay.evaluateRule(new CollectionReminderEffectiveFromCheck(TODAY.plusDays(1), TODAY)).allowed()).isTrue();
    }
}
