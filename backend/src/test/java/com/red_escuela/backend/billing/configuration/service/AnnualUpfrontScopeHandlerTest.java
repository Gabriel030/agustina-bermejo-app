package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontCheck;
import com.red_escuela.backend.billing.configuration.policy.AnnualUpfrontSettingsCoherencePolicy;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-522")
@Tag("D-524")
class AnnualUpfrontScopeHandlerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final ConfigurationTopic TOPIC = ConfigurationTopic.ofAnnualUpfront(7L, 9L);

    private final ProgramYearBillingTemplateRepository templates = mock(ProgramYearBillingTemplateRepository.class);
    private final ConfigurationPolicyEvaluator evaluator = mock(ConfigurationPolicyEvaluator.class);
    private final BillingConfigurationReader reader = mock(BillingConfigurationReader.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
    private final AnnualUpfrontScopeHandler handler = new AnnualUpfrontScopeHandler(templates, evaluator, reader, clock);

    private static YearBillingTerms termsWithDueDays(int dueDays) {
        return new YearBillingTerms(10, 1, 2, null, 1, 10, dueDays, true, false, true, true, true, false);
    }

    private AnnualUpfrontCheck validate(LocalDate termStart, Optional<YearBillingTerms> terms, LocalDate deadline) {
        ProgramYearBillingTemplate template = mock(ProgramYearBillingTemplate.class);
        when(template.termStartDate()).thenReturn(LocalDateTime.of(termStart, LocalTime.MIDNIGHT));
        when(templates.findByProgramIdAndTermId(7L, 9L)).thenReturn(Optional.of(template));
        when(reader.findYearTermsEffectiveOn(7L, 9L, TODAY)).thenReturn(terms);
        handler.requireCoherentValues(TOPIC, new AnnualUpfrontSettings(deadline, 48));
        ArgumentCaptor<AnnualUpfrontCheck> captor = ArgumentCaptor.forClass(AnnualUpfrontCheck.class);
        verify(evaluator).requireCoherentAnnualUpfrontSettings(captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "inicio {0} con {1} dias de vencimiento vence {2}")
    @CsvSource({"2027-03-01,10,2027-03-11", "2027-02-01,10,2027-02-11", "2027-03-01,0,2027-03-01", "2027-02-01,30,2027-03-03", "2027-03-15,5,2027-03-06"})
    void theFirstInstallmentDueDateIsTheFirstOfTheStartMonthPlusTheEffectiveDueDays(LocalDate termStart, int dueDays, LocalDate expected) {
        AnnualUpfrontCheck check = validate(termStart, Optional.of(termsWithDueDays(dueDays)), LocalDate.of(2027, 2, 1));

        assertThat(check.firstInstallmentDueDate()).isEqualTo(expected);
        assertThat(check.termStart()).isEqualTo(termStart);
    }

    @Test
    void withoutEffectiveYearTermsTheDueDateFallsBackToTheTermStart() {
        AnnualUpfrontCheck check = validate(LocalDate.of(2027, 2, 1), Optional.empty(), LocalDate.of(2027, 1, 20));

        assertThat(check.firstInstallmentDueDate()).isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    void forAFebruaryYearTheDeadlineIsComparedWithTheFebruaryDueDateAndNotWithMarch() {
        AnnualUpfrontSettingsCoherencePolicy policy = new AnnualUpfrontSettingsCoherencePolicy();

        AnnualUpfrontCheck onTheDay = validate(LocalDate.of(2027, 2, 1), Optional.of(termsWithDueDays(10)), LocalDate.of(2027, 2, 11));
        AnnualUpfrontCheck marchDeadline = new AnnualUpfrontCheck(new AnnualUpfrontSettings(LocalDate.of(2027, 3, 1), 48), onTheDay.termStart(), onTheDay.firstInstallmentDueDate());

        assertThat(policy.evaluateRule(onTheDay).allowed()).isTrue();
        assertThat(policy.evaluateRule(marchDeadline).allowed()).isFalse();
    }

    @Test
    void theDeadlineIsAcceptedOnTheExactDueDateAndRejectedTheNextDay() {
        AnnualUpfrontSettingsCoherencePolicy policy = new AnnualUpfrontSettingsCoherencePolicy();
        LocalDate dueDate = LocalDate.of(2027, 3, 11);

        AnnualUpfrontCheck onTheDay = validate(LocalDate.of(2027, 3, 1), Optional.of(termsWithDueDays(10)), dueDate);

        assertThat(policy.evaluateRule(onTheDay).allowed()).isTrue();
        AnnualUpfrontCheck nextDay = new AnnualUpfrontCheck(new AnnualUpfrontSettings(dueDate.plusDays(1), 48), onTheDay.termStart(), onTheDay.firstInstallmentDueDate());
        assertThat(policy.evaluateRule(nextDay).allowed()).isFalse();
    }
}
