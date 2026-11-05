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

    private static CollectionReminderSettings settings(int months, LocalTime start, LocalTime end, int cap, int contactDays,
                                                       int contactInstallments) {
        return new CollectionReminderSettings(true, months, start, end, cap, contactDays, contactInstallments);
    }

    @Test
    @Tag("D-351")
    void initialSettingsAreCoherentAndMatchTheDeclaredDefaults() {
        CollectionReminderSettings initial = CollectionReminderSettings.initial();

        assertThat(coherence.evaluateRule(initial).allowed()).isTrue();
        assertThat(initial).isEqualTo(new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2));
    }

    @ParameterizedTest(name = "{0}")
    @Tag("D-351")
    @Tag("D-373")
    @CsvSource({
            "months 0,0,09:00,11:00,250,14,2",
            "months 25,25,09:00,11:00,250,14,2",
            "window inverted,6,11:00,09:00,250,14,2",
            "window empty,6,09:00,09:00,250,14,2",
            "cap 0,6,09:00,11:00,0,14,2",
            "negative months,-1,09:00,11:00,250,14,2",
            "contact days 0,6,09:00,11:00,250,0,2",
            "contact installments 0,6,09:00,11:00,250,14,0"})
    void incoherentSettingsAreDeniedWithTheDeclaredCode(String label, int months, LocalTime start, LocalTime end, int cap,
                                                        int contactDays, int contactInstallments) {
        PolicyVerdict verdict = coherence.evaluateRule(settings(months, start, end, cap, contactDays, contactInstallments));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("INVALID_COLLECTION_REMINDER_SETTINGS");
        assertThat(CollectionReminderSettingsCoherencePolicy.class.getAnnotation(PolicyRule.class).violationCodes()).contains(verdict.code());
        assertThat(verdict.reason()).isNotBlank();
    }

    @ParameterizedTest(name = "meses post-baja {0}: permitido")
    @Tag("D-373")
    @CsvSource({"1", "24"})
    void postWithdrawalMonthBoundsAreInclusive(int months) {
        assertThat(coherence.evaluateRule(settings(months, LocalTime.of(9, 0), LocalTime.of(9, 1), 1, 1, 1)).allowed()).isTrue();
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
