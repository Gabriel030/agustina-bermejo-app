package com.red_escuela.backend.graduation.certificate.review.dto;

import java.util.List;

public record GraduationCertificateReviewBatchResponse(
        int requested,
        int decided,
        int failed,
        List<GraduationCertificateReviewBatchItemResponse> results) {
}
