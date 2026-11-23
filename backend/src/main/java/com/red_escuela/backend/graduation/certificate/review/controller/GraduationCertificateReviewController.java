package com.red_escuela.backend.graduation.certificate.review.controller;

import com.red_escuela.backend.graduation.certificate.review.dto.DecideGraduationCertificateReviewBatchRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.DisableGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewBatchResponse;
import com.red_escuela.backend.graduation.certificate.review.dto.GraduationCertificateReviewResponse;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationCertificateReviewDecisionException;
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

    @GetMapping
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public Page<GraduationCertificateReviewResponse> list(
            @RequestParam(required = false) CertificateReviewStatus status,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 25) Pageable pageable) {
        return Page.empty(pageable);
    }

    @PostMapping("/{graduationRequestId}/enable")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewResponse enable(@PathVariable Long graduationRequestId) {
        throw new InvalidGraduationCertificateReviewDecisionException(graduationRequestId);
    }

    @PostMapping("/{graduationRequestId}/disable")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewResponse disable(@PathVariable Long graduationRequestId,
                                                       @Valid @RequestBody DisableGraduationCertificateRequest request) {
        throw new InvalidGraduationCertificateReviewDecisionException(graduationRequestId);
    }

    @PostMapping("/batch")
    @RequiresPermission(code = "graduation.review_imported_certificate", legacyRoles = "ROLE_DIRECTOR")
    public GraduationCertificateReviewBatchResponse decideBatch(@Valid @RequestBody DecideGraduationCertificateReviewBatchRequest request) {
        throw new InvalidGraduationCertificateReviewDecisionException(request.graduationRequestIds().get(0));
    }
}
