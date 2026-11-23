package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;

public interface DisableGraduationCertificateUseCase {

    GraduationCertificateReviewResponse disable(Long graduationRequestId, String reason);
}
