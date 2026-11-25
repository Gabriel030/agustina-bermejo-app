package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamDocumentVerificationSource implements DocumentVerificationSource {

    private final ExamCertificateRepository repository;

    public ExamDocumentVerificationSource(ExamCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CertificateDocumentType type() {
        return CertificateDocumentType.EXAM_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
        return repository.findById(sourceId).map(certificate -> responseOf(certificate, now));
    }

    private PublicDocumentVerificationResponse responseOf(ExamCertificate certificate, LocalDateTime now) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        DocumentValidityStatus status = statusOf(certificate, now);
        return new PublicDocumentVerificationResponse(
                type(),
                status,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                status == DocumentValidityStatus.REVOKED ? certificate.getRevokedAt() : null,
                null,
                certificate.getRegistryNumber());
    }

    private DocumentValidityStatus statusOf(ExamCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == DocumentValidityStatus.REVOKED || certificate.getStatus() == DocumentValidityStatus.REPLACED) {
            return certificate.getStatus();
        }
        if (certificate.getStatus() == DocumentValidityStatus.EXPIRED || !now.isBefore(certificate.getExpiresAt())) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
