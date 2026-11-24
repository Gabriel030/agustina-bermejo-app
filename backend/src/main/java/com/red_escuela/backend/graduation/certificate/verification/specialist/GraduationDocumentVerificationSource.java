package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GraduationDocumentVerificationSource implements DocumentVerificationSource {

    private final GraduationCertificateRepository repository;

    public GraduationDocumentVerificationSource(GraduationCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CertificateDocumentType type() {
        return CertificateDocumentType.GRADUATION_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
        return repository.findById(sourceId).map(this::responseOf);
    }

    private PublicDocumentVerificationResponse responseOf(GraduationCertificate certificate) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        Optional<GraduationCertificate> replacement = certificate.getStatus() == GraduationCertificateStatus.REVOKED
                ? repository.findByReplacesId(certificate.getId())
                : Optional.empty();
        DocumentValidityStatus status = statusOf(certificate, replacement);
        return new PublicDocumentVerificationResponse(
                type(),
                status,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                certificate.getIssuedAt(),
                null,
                status == DocumentValidityStatus.REVOKED ? certificate.getRevokedAt() : null,
                replacement.map(GraduationCertificate::getIssuedAt).orElse(null),
                certificate.getRegistryNumber());
    }

    private DocumentValidityStatus statusOf(GraduationCertificate certificate, Optional<GraduationCertificate> replacement) {
        if (certificate.getStatus() != GraduationCertificateStatus.REVOKED) {
            return DocumentValidityStatus.VALID;
        }
        return replacement.isPresent() ? DocumentValidityStatus.REPLACED : DocumentValidityStatus.REVOKED;
    }
}
