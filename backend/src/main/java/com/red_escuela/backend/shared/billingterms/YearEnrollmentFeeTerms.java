package com.red_escuela.backend.shared.billingterms;

public record YearEnrollmentFeeTerms(int enrollmentFeeCount, boolean blocksEnrollment, boolean blocksExamRegistration, boolean blocksCertificates) {

    public boolean blocksAnyProcedure() {
        return blocksEnrollment || blocksExamRegistration || blocksCertificates;
    }
}
