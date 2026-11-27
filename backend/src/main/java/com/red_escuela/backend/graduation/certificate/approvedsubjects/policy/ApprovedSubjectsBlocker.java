package com.red_escuela.backend.graduation.certificate.approvedsubjects.policy;

public enum ApprovedSubjectsBlocker {
    NO_ACTIVE_ENROLLMENT,
    NOT_REGULAR_STUDENT,
    PENDING_PAYMENTS,
    ISSUANCE_LIMIT_REACHED,
    NO_APPROVED_SUBJECTS
}
