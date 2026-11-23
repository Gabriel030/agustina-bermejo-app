package com.red_escuela.backend.graduation.certificate.review.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.lang.Nullable;

public record DecideGraduationCertificateReviewBatchRequest(
        @NotNull @Size(min = 1, max = 50) List<@NotNull Long> graduationRequestIds,
        @NotNull CertificateReviewDecision decision,
        @Nullable @Size(min = 10, max = 500) String reason) {
}
