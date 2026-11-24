package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamRegistrationDocumentVerificationSource implements DocumentVerificationSource {

    private final ExamRegistrationCertificateRepository repository;

    public ExamRegistrationDocumentVerificationSource(ExamRegistrationCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    public CertificateDocumentType type() {
        return CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicDocumentVerificationResponse> find(Long sourceId, LocalDateTime now) {
        return repository.findById(sourceId).map(certificate -> responseOf(certificate, now));
    }

    private PublicDocumentVerificationResponse responseOf(ExamRegistrationCertificate certificate, LocalDateTime now) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        Optional<ExamRegistrationCertificate> replacement = certificate.getStatus() == DocumentValidityStatus.REPLACED
                ? repository.findFirstByRegistrationIdAndIdGreaterThanOrderByIdAsc(certificate.getRegistrationId(), certificate.getId())
                : Optional.empty();
        DocumentValidityStatus status = statusOf(certificate, now);
        return new PublicDocumentVerificationResponse(
                type(),
                status,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                status == DocumentValidityStatus.REVOKED ? certificate.getRevokedAt() : null,
                status == DocumentValidityStatus.REPLACED ? replacement.map(ExamRegistrationCertificate::getIssuedAt).orElse(null) : null,
                certificate.getRegistryNumber());
    }

    private DocumentValidityStatus statusOf(ExamRegistrationCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == DocumentValidityStatus.REVOKED || certificate.getStatus() == DocumentValidityStatus.REPLACED) {
            return certificate.getStatus();
        }
        if (certificate.getStatus() == DocumentValidityStatus.EXPIRED || !now.isBefore(certificate.getExpiresAt())) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
