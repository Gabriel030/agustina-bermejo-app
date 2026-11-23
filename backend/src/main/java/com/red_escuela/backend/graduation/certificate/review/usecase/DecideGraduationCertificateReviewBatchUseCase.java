package com.red_escuela.backend.graduation.certificate.review.usecase;

import com.red_escuela.backend.graduation.certificate.review.dto.DecideGraduationCertificateReviewBatchRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewBatchResponse;

public interface DecideGraduationCertificateReviewBatchUseCase {

    GraduationCertificateReviewBatchResponse decideBatch(DecideGraduationCertificateReviewBatchRequest request);
}
