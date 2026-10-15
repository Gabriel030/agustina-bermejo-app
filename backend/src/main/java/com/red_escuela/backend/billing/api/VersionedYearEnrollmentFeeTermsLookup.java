package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTerms;
import com.red_escuela.backend.shared.billingterms.YearEnrollmentFeeTermsLookup;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class VersionedYearEnrollmentFeeTermsLookup implements YearEnrollmentFeeTermsLookup {
    private final BillingConfigurationReader configurationReader;

    public VersionedYearEnrollmentFeeTermsLookup(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    @Override
    @Transactional(readOnly = true)
    public YearEnrollmentFeeTerms requireEnrollmentFeeTermsEffectiveOn(Long programId, Long termId, LocalDate date) {
        YearBillingTerms terms = configurationReader.requireYearTermsEffectiveOn(programId, termId, date);
        return new YearEnrollmentFeeTerms(terms.enrollmentFeeCount(), terms.blocksEnrollment(), terms.blocksExamRegistration(), terms.blocksCertificates());
    }
}
