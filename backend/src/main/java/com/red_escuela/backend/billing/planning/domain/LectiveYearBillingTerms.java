package com.red_escuela.backend.billing.planning.domain;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;

public record LectiveYearBillingTerms(boolean paysWhilePaused, boolean requireEnrollmentFeeForFinalExamOnlyStudents,
                                      int monthlyInstallmentsCount, int monthlyInstallmentDueDays, int lateEnrollmentFeeDueDays,
                                      int enrollmentFeeLeadMonths, boolean blocksSubjectEnrollment, boolean blocksExamRegistration,
                                      boolean blocksCertificates) {

    public static LectiveYearBillingTerms of(YearBillingTerms terms) {
        return new LectiveYearBillingTerms(terms.paysWhilePaused(), terms.requireEnrollmentFeeForFinalExamOnlyStudents(),
                terms.recurringFeeInvoicesCount(), terms.recurringFeeDueDays(), terms.lateEnrollmentFeeDueDays(),
                terms.enrollmentFeeLeadMonths(), terms.blocksEnrollment(), terms.blocksExamRegistration(),
                terms.blocksCertificates());
    }

    public boolean blocks(EnrollmentFeeRequirementScope scope) {
        return scope.blockedBy(this);
    }
}
