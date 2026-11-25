package com.red_escuela.backend.graduation.certificate.approvedsubjects.port;

public interface ApprovedSubjectsAuditLog {

    void certificateRevoked(Long certificateId, String reason);
}
