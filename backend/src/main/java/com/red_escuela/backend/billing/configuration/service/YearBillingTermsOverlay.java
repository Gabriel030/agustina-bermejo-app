package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class YearBillingTermsOverlay {

    public YearBillingTerms overlay(YearBillingTerms base, YearBillingConfigurationRequest request) {
        if (request == null) {
            return base;
        }
        int count = Optional.ofNullable(request.enrollmentFeeCount()).orElse(base.enrollmentFeeCount());
        Integer second = count == 1 ? null : Optional.ofNullable(request.secondEnrollmentFeeMonth()).orElse(base.secondEnrollmentFeeMonth());
        return new YearBillingTerms(base.recurringFeeInvoicesCount(), count, Optional.ofNullable(request.firstEnrollmentFeeMonth()).orElse(base.firstEnrollmentFeeMonth()), second,
                Optional.ofNullable(request.enrollmentFeeLeadMonths()).orElse(base.enrollmentFeeLeadMonths()),
                Optional.ofNullable(request.lateEnrollmentFeeDueDays()).orElse(base.lateEnrollmentFeeDueDays()),
                Optional.ofNullable(request.recurringFeeDueDays()).orElse(base.recurringFeeDueDays()),
                Optional.ofNullable(request.withdrawSuspendedAtTermEnd()).orElse(base.withdrawSuspendedAtTermEnd()),
                Optional.ofNullable(request.paysWhilePaused()).orElse(base.paysWhilePaused()),
                Optional.ofNullable(request.blocksEnrollment()).orElse(base.blocksEnrollment()),
                Optional.ofNullable(request.blocksExamRegistration()).orElse(base.blocksExamRegistration()),
                Optional.ofNullable(request.blocksCertificates()).orElse(base.blocksCertificates()),
                Optional.ofNullable(request.requireEnrollmentFeeForFinalExamOnlyStudents()).orElse(base.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }
}
