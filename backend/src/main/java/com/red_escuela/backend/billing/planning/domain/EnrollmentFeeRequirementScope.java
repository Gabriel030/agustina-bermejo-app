package com.red_escuela.backend.billing.planning.domain;

import java.util.function.Predicate;

public enum EnrollmentFeeRequirementScope {
    SUBJECT_ENROLLMENT(LectiveYearBillingTerms::blocksSubjectEnrollment),
    EXAM_REGISTRATION(LectiveYearBillingTerms::blocksExamRegistration),
    CERTIFICATE(LectiveYearBillingTerms::blocksCertificates);

    private final Predicate<LectiveYearBillingTerms> blockedByTerms;

    EnrollmentFeeRequirementScope(Predicate<LectiveYearBillingTerms> blockedByTerms) {
        this.blockedByTerms = blockedByTerms;
    }

    boolean blockedBy(LectiveYearBillingTerms terms) {
        return blockedByTerms.test(terms);
    }
}
