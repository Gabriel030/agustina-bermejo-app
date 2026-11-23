package com.red_escuela.backend.graduation.certificate.review.specialist;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.review.dto.CertificateReviewDecision;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationRequestNotFoundException;
import com.red_escuela.backend.graduation.exception.InvalidGraduationCertificateReviewDecisionException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GraduationCertificateReviewDecider {

    private static final int MIN_REASON_LENGTH = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationCertificateRepository graduationCertificateRepository;
    private final CurrentActorFinder currentActorFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public GraduationCertificateReviewDecider(GraduationRequestRepository graduationRequestRepository,
                                              GraduationCertificateRepository graduationCertificateRepository,
                                              CurrentActorFinder currentActorFinder, AuditTrail auditTrail, Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationCertificateRepository = graduationCertificateRepository;
        this.currentActorFinder = currentActorFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    public void requireValidReason(CertificateReviewDecision decision, String rawReason) {
        if (decision == CertificateReviewDecision.DISABLE) {
            normalizedDisableReason(rawReason);
        }
    }

    @Transactional
    public Long decide(Long graduationRequestId, CertificateReviewDecision decision, String rawReason) {
        String reason = decision == CertificateReviewDecision.DISABLE ? normalizedDisableReason(rawReason) : null;
        GraduationRequest request = graduationRequestRepository.findByIdForUpdate(graduationRequestId)
                .orElseThrow(() -> new GraduationRequestNotFoundException(graduationRequestId));
        CertificateReviewStatus before = request.getCertificateReviewStatus();
        CertificateReviewStatus after = decision == CertificateReviewDecision.ENABLE
                ? CertificateReviewStatus.ENABLED : CertificateReviewStatus.DISABLED;
        requireDecidable(request, after);
        request.setCertificateReviewStatus(after);
        request.setCertificateReviewedBy(currentActorFinder.currentPerson());
        request.setCertificateReviewedAt(LocalDateTime.now(clock));
        graduationRequestRepository.saveAndFlush(request);
        auditTrail.record(new AuditRecord(
                after == CertificateReviewStatus.ENABLED
                        ? AuditAction.GRADUATION_CERTIFICATE_REVIEW_ENABLED : AuditAction.GRADUATION_CERTIFICATE_REVIEW_DISABLED,
                AuditEntityType.GRADUATION_REQUEST, request.getId(), before.name(), after.name(), reason));
        return request.getId();
    }

    private void requireDecidable(GraduationRequest request, CertificateReviewStatus target) {
        boolean reviewable = request.getStatus() == GraduationRequestStatus.APPROVED
                && request.getCertificateReviewStatus() != CertificateReviewStatus.NOT_REQUIRED
                && request.getCertificateReviewStatus() != target;
        if (!reviewable || hasIssuedCertificate(request)) {
            throw new InvalidGraduationCertificateReviewDecisionException(request.getId());
        }
    }

    private boolean hasIssuedCertificate(GraduationRequest request) {
        return graduationCertificateRepository.existsByProgramEnrollmentIdAndStatus(
                request.getProgramEnrollment().getId(), GraduationCertificateStatus.VALID);
    }

    private String normalizedDisableReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            throw new RequestFieldRequiredException("El motivo es obligatorio para deshabilitar el certificado");
        }
        String reason = rawReason.trim();
        if (reason.length() < MIN_REASON_LENGTH || reason.length() > MAX_REASON_LENGTH) {
            throw new RequestFieldOutOfRangeException("El motivo debe tener entre 10 y 500 caracteres");
        }
        return reason;
    }
}
