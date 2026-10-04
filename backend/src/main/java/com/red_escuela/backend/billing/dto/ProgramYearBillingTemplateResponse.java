package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public record ProgramYearBillingTemplateResponse(Long id, Long programId, Long termId, Integer recurringFeeInvoicesCount,
                                                 Integer enrollmentFeeCount, Integer firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                                                 Integer enrollmentFeeLeadMonths, Integer lateEnrollmentFeeDueDays, Integer recurringFeeDueDays, boolean withdrawSuspendedAtTermEnd, boolean paysWhilePaused, boolean blocksEnrollment, boolean blocksExamRegistration,
                                                 boolean blocksCertificates, boolean requireEnrollmentFeeForFinalExamOnlyStudents) {

    public static ProgramYearBillingTemplateResponse from(ProgramYearBillingTemplate template) {
        return new ProgramYearBillingTemplateResponse(
                template.getId(), template.getProgram().getId(), template.getTerm().getId(), template.getRecurringFeeInvoicesCount(),
                template.getEnrollmentFeeCount(), template.getFirstEnrollmentFeeMonth(), template.getSecondEnrollmentFeeMonth(),
                template.getEnrollmentFeeLeadMonths(), template.getLateEnrollmentFeeDueDays(), template.getRecurringFeeDueDays(), template.isWithdrawSuspendedAtTermEnd(), template.isPaysWhilePaused(), template.isBlocksEnrollment(), template.isBlocksExamRegistration(),
                template.isBlocksCertificates(), template.isRequireEnrollmentFeeForFinalExamOnlyStudents());
    }
}
