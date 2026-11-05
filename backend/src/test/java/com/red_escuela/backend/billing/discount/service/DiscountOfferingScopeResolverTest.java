package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.academicstructure.api.OfferingCurrencyFinder;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.discount.exception.DiscountScopeOfferingNotFoundException;
import com.red_escuela.backend.billing.discount.policy.DiscountDefinitionCheck;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramCohortNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-097")
class DiscountOfferingScopeResolverTest {
    private final OfferingCurrencyFinder currencyFinder = mock(OfferingCurrencyFinder.class);
    private final ProgramCohortRepository cohortRepository = mock(ProgramCohortRepository.class);
    private final DiscountOfferingScopeResolver resolver = new DiscountOfferingScopeResolver(currencyFinder, cohortRepository);

    @Test
    void aProgramScopeResolvesTheCurrencyOfTheProgram() {
        when(currencyFinder.findProgramCurrency(4L)).thenReturn(Optional.of(Currency.USD));

        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).build());

        assertThat(check.offeringCurrency()).isEqualTo(Currency.USD);
        assertThat(check.cohortProgramId()).isNull();
    }

    @Test
    void aCourseScopeResolvesTheCurrencyOfTheCourse() {
        when(currencyFinder.findCourseCurrency(9L)).thenReturn(Optional.of(Currency.ARS));

        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forCourse(9L).build());

        assertThat(check.offeringCurrency()).isEqualTo(Currency.ARS);
    }

    @Test
    void aCohortScopeResolvesTheProgramTheCohortBelongsTo() {
        when(currencyFinder.findProgramCurrency(4L)).thenReturn(Optional.of(Currency.ARS));
        ProgramCohort cohort = mock(ProgramCohort.class);
        ProgramYearBillingTemplate template = mock(ProgramYearBillingTemplate.class);
        when(template.programId()).thenReturn(77L);
        when(cohort.getTemplate()).thenReturn(template);
        when(cohortRepository.findById(8L)).thenReturn(Optional.of(cohort));

        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).forCohort(8L).build());

        assertThat(check.cohortProgramId()).isEqualTo(77L);
    }

    @Test
    void aDraftWithoutScopeResolvesNothing() {
        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.annualUpfront().build());

        assertThat(check.offeringCurrency()).isNull();
        assertThat(check.cohortProgramId()).isNull();
    }

    @Test
    void missingOfferingsAndCohortsAreNotFound() {
        when(currencyFinder.findProgramCurrency(4L)).thenReturn(Optional.empty());
        when(currencyFinder.findCourseCurrency(9L)).thenReturn(Optional.empty());
        when(cohortRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).build())).isInstanceOf(DiscountScopeOfferingNotFoundException.class);
        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forCourse(9L).build())).isInstanceOf(DiscountScopeOfferingNotFoundException.class);
        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.annualUpfront().forCohort(8L).build())).isInstanceOf(ProgramCohortNotFoundException.class);
    }
}
