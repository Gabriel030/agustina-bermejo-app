package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;

final class CertificateExceptionResponses {

    private CertificateExceptionResponses() {
    }

    static CertificateExceptionResponse of(CertificateExceptionApproval approval) {
        return new CertificateExceptionResponse(approval.getId(), approval.getStudentId(), approval.getKind(), approval.enrollmentId(),
                approval.getOrganismKind(), approval.getOrganismReference(), approval.getReason(), approval.getStatus(),
                approval.getRequestedById(), approval.getRequestedAt(), approval.getDecidedById(), approval.getDecidedAt(),
                approval.getDecisionReason(), approval.getValidUntil(), approval.isCreatedByDirector(),
                approval.getDebtAtRequest(),
                approval.getOverdueInstallmentsAtRequest());
    }
}
