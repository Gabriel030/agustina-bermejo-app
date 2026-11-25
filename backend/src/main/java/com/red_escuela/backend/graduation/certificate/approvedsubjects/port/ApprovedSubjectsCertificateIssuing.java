package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;

import java.time.LocalDateTime;

public interface ApprovedSubjectsCertificateIssuing {

    ApprovedSubjectsCertificateResponse issue(Long studentId, Long enrollmentId, Long issuedById, LocalDateTime now);
}
