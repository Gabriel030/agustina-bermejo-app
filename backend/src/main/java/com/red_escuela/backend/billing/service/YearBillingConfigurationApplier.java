package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import org.springframework.stereotype.Component;

@Component
public class YearBillingConfigurationApplier {

    public void applyYearBillingConfiguration(ProgramYearBillingTemplate template, YearBillingConfigurationRequest request) {
        if (request == null) {
            return;
        }
        int count = request.enrollmentFeeCount() != null ? request.enrollmentFeeCount() : template.getEnrollmentFeeCount();
        int first = request.firstEnrollmentFeeMonth() != null ? request.firstEnrollmentFeeMonth() : template.getFirstEnrollmentFeeMonth();
        Integer second = request.secondEnrollmentFeeMonth() != null ? request.secondEnrollmentFeeMonth() : template.getSecondEnrollmentFeeMonth();
        template.configureEnrollmentFees(count, first, second);
        if (request.enrollmentFeeLeadMonths() != null) {
            template.setEnrollmentFeeLeadMonths(request.enrollmentFeeLeadMonths());
        }
        if (request.lateEnrollmentFeeDueDays() != null) {
            template.setLateEnrollmentFeeDueDays(request.lateEnrollmentFeeDueDays());
        }
        if (request.recurringFeeDueDays() != null) {
            template.setRecurringFeeDueDays(request.recurringFeeDueDays());
        }
        if (request.withdrawSuspendedAtTermEnd() != null) {
            template.setWithdrawSuspendedAtTermEnd(request.withdrawSuspendedAtTermEnd());
        }
        if (request.paysWhilePaused() != null) {
            template.setPaysWhilePaused(request.paysWhilePaused());
        }
        if (request.blocksEnrollment() != null) {
            template.setBlocksEnrollment(request.blocksEnrollment());
        }
        if (request.blocksExamRegistration() != null) {
            template.setBlocksExamRegistration(request.blocksExamRegistration());
        }
        if (request.blocksCertificates() != null) {
            template.setBlocksCertificates(request.blocksCertificates());
        }
        if (request.requireEnrollmentFeeForFinalExamOnlyStudents() != null) {
            template.setRequireEnrollmentFeeForFinalExamOnlyStudents(request.requireEnrollmentFeeForFinalExamOnlyStudents());
        }
    }
}
