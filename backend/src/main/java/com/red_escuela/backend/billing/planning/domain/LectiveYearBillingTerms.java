package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public record LectiveYearBillingTerms(boolean paysWhilePaused, boolean requireEnrollmentFeeForFinalExamOnlyStudents,
                                      int monthlyInstallmentsCount, int monthlyInstallmentDueDays, int lateEnrollmentFeeDueDays,
                                      int enrollmentFeeLeadMonths, boolean blocksSubjectEnrollment, boolean blocksExamRegistration,
                                      boolean blocksCertificates) {

    public static LectiveYearBillingTerms fromTemplate(ProgramYearBillingTemplate template) {
        return new LectiveYearBillingTerms(template.isPaysWhilePaused(), template.isRequireEnrollmentFeeForFinalExamOnlyStudents(),
                template.getRecurringFeeInvoicesCount(), template.getRecurringFeeDueDays(), template.getLateEnrollmentFeeDueDays(),
                template.getEnrollmentFeeLeadMonths(), template.isBlocksEnrollment(), template.isBlocksExamRegistration(),
                template.isBlocksCertificates());
    }

    public boolean blocks(EnrollmentFeeRequirementScope scope) {
        return scope.blockedBy(this);
    }
}
