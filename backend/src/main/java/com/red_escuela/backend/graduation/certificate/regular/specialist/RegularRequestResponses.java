package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;

final class RegularRequestResponses {

    private RegularRequestResponses() {
    }

    static RegularCertificateRequestResponse of(RegularCertificateRequest request, RegularEnrollmentFacts facts) {
        return of(request, facts.studentName(), facts.titleName());
    }

    static RegularCertificateRequestResponse of(RegularCertificateRequest request, String studentName, String programName) {
        return of(request, studentName, programName, null, null);
    }

    static RegularCertificateRequestResponse of(RegularCertificateRequest request, String studentName, String programName,
                                                RegularDebtFacts debt, CertificateExceptionApproval openException) {
        return new RegularCertificateRequestResponse(request.getId(), request.getStudentId(), studentName, programName,
                request.getKind(), request.enrollmentId(), request.getChannel(), request.getOutcome(), request.getReasons(),
                request.getRequestedAt(), request.getCertificateId(), request.getRejectionReason(),
                debt == null ? null : debt.totalDue(), debt == null ? null : debt.overdueInstallments(),
                debt == null ? null : debt.hasDebt(), openException == null ? null : openException.getId(),
                openException == null ? null : openException.getStatus(), openException == null ? null : openException.getValidUntil());
    }
}
