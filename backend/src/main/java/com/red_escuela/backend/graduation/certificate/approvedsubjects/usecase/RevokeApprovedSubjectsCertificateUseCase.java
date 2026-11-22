package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;

public interface RevokeApprovedSubjectsCertificateUseCase {

    ApprovedSubjectsCertificateResponse revoke(Long id, String reason);
}
