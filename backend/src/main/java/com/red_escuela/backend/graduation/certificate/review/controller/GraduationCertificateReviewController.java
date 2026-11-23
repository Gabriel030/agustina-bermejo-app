package com.red_escuela.backend.graduation.certificate.review.controller;

import com.red_escuela.backend.graduation.certificate.review.dto.DecideGraduationCertificateReviewBatchRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.DisableGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewBatchResponse;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.certificate.review.usecase.DecideGraduationCertificateReviewBatchUseCase;
import com.red_escuela.backend.graduation.certificate.review.usecase.DisableGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.review.usecase.EnableGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.review.usecase.ListGraduationCertificateReviewsUseCase;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/graduation-certificate-reviews")
public class GraduationCertificateReviewController {

    private final ListGraduationCertificateReviewsUseCase listReviews;
    private final EnableGraduationCertificateUseCase enableCertificate;
    private final DisableGraduationCertificateUseCase disableCertificate;
    private final DecideGraduationCertificateReviewBatchUseCase decideBatch;

    public GraduationCertificateReviewController(ListGraduationCertificateReviewsUseCase listReviews,
                                                 EnableGraduationCertificateUseCase enableCertificate,
                                                 DisableGraduationCertificateUseCase disableCertificate,
                                                 DecideGraduationCertificateReviewBatchUseCase decideBatch) {
        this.listReviews = listReviews;
        this.enableCertificate = enableCertificate;
        this.disableCertificate = disableCertificate;
        this.decideBatch = decideBatch;
    }

    @GetMapping
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public Page<GraduationCertificateReviewResponse> list(
            @RequestParam(required = false) CertificateReviewStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 25) Pageable pageable) {
        return listReviews.list(status, query, pageable);
    }

    @PostMapping("/{graduationRequestId}/enable")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewResponse enable(@PathVariable Long graduationRequestId) {
        return enableCertificate.enable(graduationRequestId);
    }

    @PostMapping("/{graduationRequestId}/disable")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewResponse disable(@PathVariable Long graduationRequestId,
                                                       @Valid @RequestBody DisableGraduationCertificateRequest request) {
        return disableCertificate.disable(graduationRequestId, request.reason());
    }

    @PostMapping("/batch")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewBatchResponse decideBatch(@Valid @RequestBody DecideGraduationCertificateReviewBatchRequest request) {
        return decideBatch.decideBatch(request);
    }
}
