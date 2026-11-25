package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

public interface ApprovedSubjectsRefusals {

    RuntimeException enrollmentNotFound();

    RuntimeException notEligible();

    RuntimeException issuanceLimitReached();
}
