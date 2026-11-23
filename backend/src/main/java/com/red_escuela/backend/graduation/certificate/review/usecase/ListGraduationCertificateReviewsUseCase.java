package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListGraduationCertificateReviewsUseCase {

    Page<GraduationCertificateReviewResponse> list(CertificateReviewStatus status, String query, Pageable pageable);
}
