package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class YearBillingConfigurationDefaults {
    private static final int DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS = 1;

    public YearBillingConfigurationRequest completeForCreation(Term term, YearBillingConfigurationRequest request) {
        YearBillingConfigurationRequest base = request != null ? request : YearBillingConfigurationRequest.defaults();
        if (base.firstEnrollmentFeeMonth() != null || term.getStartDate() == null) {
            return base;
        }
        int leadMonths = base.enrollmentFeeLeadMonths() != null ? base.enrollmentFeeLeadMonths() : DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS;
        int firstMonth = YearMonth.from(term.getStartDate()).minusMonths(leadMonths).getMonthValue();
        return new YearBillingConfigurationRequest(base.enrollmentFeeCount(), firstMonth, base.secondEnrollmentFeeMonth(),
                base.enrollmentFeeLeadMonths(), base.lateEnrollmentFeeDueDays(), base.recurringFeeDueDays(), base.withdrawSuspendedAtTermEnd(),
                base.paysWhilePaused(), base.blocksEnrollment(), base.blocksExamRegistration(), base.blocksCertificates(),
                base.requireEnrollmentFeeForFinalExamOnlyStudents());
    }
}
