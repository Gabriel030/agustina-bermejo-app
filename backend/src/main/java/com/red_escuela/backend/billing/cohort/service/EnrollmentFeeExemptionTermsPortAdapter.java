package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.InvalidEnrollmentFeeConfigurationException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateMismatchException;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeExemptionTermsPort;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeExemptionTermsView;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTermsLookup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class EnrollmentFeeExemptionTermsPortAdapter implements EnrollmentFeeExemptionTermsPort {

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearEnrollmentFeeTermsLookup termsLookup;

    public EnrollmentFeeExemptionTermsPortAdapter(ProgramYearBillingTemplateRepository templateRepository, YearEnrollmentFeeTermsLookup termsLookup) {
        this.templateRepository = templateRepository;
        this.termsLookup = termsLookup;
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentFeeExemptionTermsView requireTermsForExemption(Long templateId, Long programId, int slot, LocalDate effectiveOn) {
        ProgramYearBillingTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(templateId));
        if (!template.getProgram().getId().equals(programId)) {
            throw new ProgramYearBillingTemplateMismatchException(template.getId());
        }
        YearEnrollmentFeeTerms terms = termsLookup.requireEnrollmentFeeTermsEffectiveOn(template.programId(), template.termId(), effectiveOn);
        if (slot > terms.enrollmentFeeCount()) {
            throw new InvalidEnrollmentFeeConfigurationException("El año lectivo solo tiene " + terms.enrollmentFeeCount() + " matrícula(s)");
        }
        return new EnrollmentFeeExemptionTermsView(template.getId(), terms.blocksAnyProcedure());
    }
}
