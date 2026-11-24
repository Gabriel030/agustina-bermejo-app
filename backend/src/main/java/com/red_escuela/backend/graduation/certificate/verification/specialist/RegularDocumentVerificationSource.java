package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegularDocumentVerificationSource implements DocumentVerificationSource {

    private final RegularStudentCertificateRepository repository;

    public RegularDocumentVerificationSource(RegularStudentCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CertificateDocumentType type() {
        return CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
        return repository.findById(sourceId).map(certificate -> responseOf(certificate, now));
    }

    private PublicDocumentVerificationResponse responseOf(RegularStudentCertificate certificate, LocalDateTime now) {
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

    private DocumentValidityStatus statusOf(RegularStudentCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == RegularCertificateStatus.REVOKED) {
            return DocumentValidityStatus.REVOKED;
        }
        if (certificate.getStatus() == RegularCertificateStatus.EXPIRED || certificate.getExpiresAt().isBefore(now)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
