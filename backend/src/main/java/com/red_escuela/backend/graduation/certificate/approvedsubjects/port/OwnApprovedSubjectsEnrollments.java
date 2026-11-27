package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import java.util.Optional;

public interface OwnApprovedSubjectsEnrollments {

    Optional<OwnedEnrollment> findOwnedBy(Long studentId, Long enrollmentId);
}
