package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;

@Component
public class YearBillingConfigurationDefaults {
    private static final int DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS = 1;

    public YearBillingConfigurationRequest completeForCreation(LocalDateTime termStart, YearBillingConfigurationRequest request) {
        YearBillingConfigurationRequest base = request != null ? request : YearBillingConfigurationRequest.defaults();
        if (base.firstEnrollmentFeeMonth() != null || termStart == null) {
            return base;
        }
        int leadMonths = base.enrollmentFeeLeadMonths() != null ? base.enrollmentFeeLeadMonths() : DEFAULT_ENROLLMENT_FEE_LEAD_MONTHS;
        int firstMonth = YearMonth.from(termStart).minusMonths(leadMonths).getMonthValue();
        return new YearBillingConfigurationRequest(base.enrollmentFeeCount(), firstMonth, base.secondEnrollmentFeeMonth(),
                base.enrollmentFeeLeadMonths(), base.lateEnrollmentFeeDueDays(), base.recurringFeeDueDays(), base.withdrawSuspendedAtTermEnd(),
                base.paysWhilePaused(), base.blocksEnrollment(), base.blocksExamRegistration(), base.blocksCertificates(),
                base.requireEnrollmentFeeForFinalExamOnlyStudents());
    }
}
