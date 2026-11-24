package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;

public interface EnableGraduationCertificateUseCase {

    GraduationCertificateReviewResponse enable(Long graduationRequestId);
}
