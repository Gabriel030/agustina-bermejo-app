package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class ApprovedSubjectsDocumentVerificationSource implements DocumentVerificationSource {

    private final ApprovedSubjectsCertificateRepository repository;

    public ApprovedSubjectsDocumentVerificationSource(ApprovedSubjectsCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CertificateDocumentType type() {
        return CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
        return repository.findById(sourceId).map(certificate -> responseOf(certificate, now));
    }

    private PublicDocumentVerificationResponse responseOf(ApprovedSubjectsCertificate certificate, LocalDateTime now) {
        DocumentValidityStatus status = statusOf(certificate, now);
        return new PublicDocumentVerificationResponse(
                type(),
                status,
                certificate.getSnapshot().holderFullName(),
                DocumentMasker.mask(certificate.getSnapshot().documentNumber()),
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                status == DocumentValidityStatus.REVOKED ? certificate.getRevokedAt() : null,
                null,
                certificate.getRegistryNumber());
    }

    private DocumentValidityStatus statusOf(ApprovedSubjectsCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == ApprovedSubjectsCertificateStatus.REVOKED) {
            return DocumentValidityStatus.REVOKED;
        }
        if (certificate.getStatus() == ApprovedSubjectsCertificateStatus.EXPIRED || certificate.isExpiredAt(now)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
