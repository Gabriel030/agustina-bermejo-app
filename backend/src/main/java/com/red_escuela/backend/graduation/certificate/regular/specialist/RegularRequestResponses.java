package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;

final class RegularRequestResponses {

    private RegularRequestResponses() {
    }

    static RegularCertificateRequestResponse of(RegularCertificateRequest request) {
        return new RegularCertificateRequestResponse(request.getId(), request.getStudentId(), request.getKind(),
                request.enrollmentId(), request.getChannel(), request.getOutcome(), request.getReasons(),
                request.getRequestedAt(), request.getCertificateId());
    }
}
