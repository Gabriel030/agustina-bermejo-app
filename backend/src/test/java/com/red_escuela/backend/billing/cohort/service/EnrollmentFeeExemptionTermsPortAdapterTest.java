package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeExemptionTermsView;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTermsLookup;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentFeeExemptionTermsPortAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);

    @Mock
    private ProgramYearBillingTemplateRepository templateRepository;
    @Mock
    private YearEnrollmentFeeTermsLookup termsLookup;

    private EnrollmentFeeExemptionTermsPortAdapter adapter;
    private ProgramYearBillingTemplate template;

    @BeforeEach
    void setUp() {
        adapter = new EnrollmentFeeExemptionTermsPortAdapter(templateRepository, termsLookup);
        Program program = new Program();
        program.setId(7L);
        Term term = TermFixtures.newTerm();
        term.setId(3L);
        template = new ProgramYearBillingTemplate();
        template.setId(20L);
        template.setProgram(program);
        template.setTerm(term);
    }

    private void givenTerms(int feeCount, boolean blocksEnrollment) {
        when(termsLookup.requireEnrollmentFeeTermsEffectiveOn(7L, 3L, TODAY)).thenReturn(new YearEnrollmentFeeTerms(feeCount, blocksEnrollment, false, false));
    }

    @Test
    void returnsTemplateIdAndBlockingFlagWhenSlotFitsTheTerm() {
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));
        givenTerms(2, true);

        EnrollmentFeeExemptionTermsView view = adapter.requireTermsForExemption(20L, 7L, 2, TODAY);

        assertThat(view).isEqualTo(new EnrollmentFeeExemptionTermsView(20L, true));
    }

    @Test
    void failsWhenTemplateDoesNotExist() {
        when(templateRepository.findById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.requireTermsForExemption(20L, 7L, 1, TODAY)).isInstanceOf(ProgramYearBillingTemplateNotFoundException.class);
    }

    @Test
    void failsWhenTemplateBelongsToAnotherProgram() {
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> adapter.requireTermsForExemption(20L, 8L, 1, TODAY)).isInstanceOf(ProgramYearBillingTemplateMismatchException.class);
    }

    @Test
    void failsWhenSlotExceedsEnrollmentFeesOfTheTerm() {
        when(templateRepository.findById(20L)).thenReturn(Optional.of(template));
        givenTerms(1, true);

        assertThatThrownBy(() -> adapter.requireTermsForExemption(20L, 7L, 2, TODAY)).isInstanceOf(InvalidEnrollmentFeeConfigurationException.class);
    }
}
