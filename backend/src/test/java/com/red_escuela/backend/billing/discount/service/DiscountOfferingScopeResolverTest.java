package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
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
    private final ProgramRepository programRepository = mock(ProgramRepository.class);
    private final CourseRepository courseRepository = mock(CourseRepository.class);
    private final ProgramCohortRepository cohortRepository = mock(ProgramCohortRepository.class);
    private final DiscountOfferingScopeResolver resolver = new DiscountOfferingScopeResolver(programRepository, courseRepository, cohortRepository);

    @Test
    void aProgramScopeResolvesTheCurrencyOfTheProgram() {
        Program program = mock(Program.class);
        when(program.getCurrency()).thenReturn(Currency.USD);
        when(programRepository.findById(4L)).thenReturn(Optional.of(program));

        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).build());

        assertThat(check.offeringCurrency()).isEqualTo(Currency.USD);
        assertThat(check.cohortProgramId()).isNull();
    }

    @Test
    void aCourseScopeResolvesTheCurrencyOfTheCourse() {
        Course course = mock(Course.class);
        when(course.getCurrency()).thenReturn(Currency.ARS);
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));

        DiscountDefinitionCheck check = resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forCourse(9L).build());

        assertThat(check.offeringCurrency()).isEqualTo(Currency.ARS);
    }

    @Test
    void aCohortScopeResolvesTheProgramTheCohortBelongsTo() {
        Program program = mock(Program.class);
        when(program.getCurrency()).thenReturn(Currency.ARS);
        when(programRepository.findById(4L)).thenReturn(Optional.of(program));
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
        when(programRepository.findById(4L)).thenReturn(Optional.empty());
        when(courseRepository.findById(9L)).thenReturn(Optional.empty());
        when(cohortRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forProgram(4L).build())).isInstanceOf(ProgramNotFoundException.class);
        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.cohortPromotion().forCourse(9L).build())).isInstanceOf(CourseNotFoundException.class);
        assertThatThrownBy(() -> resolver.resolveScopeOf(DiscountDraftBuilder.annualUpfront().forCohort(8L).build())).isInstanceOf(ProgramCohortNotFoundException.class);
    }
}
