package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateDocumentKind;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class ExamRegistrationVerificationSource implements CertificateVerificationSource {

    private final ExamRegistrationCertificateRepository repository;

    public ExamRegistrationVerificationSource(ExamRegistrationCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicCertificateView> findByCode(CertificateCode code, LocalDateTime now) {
        return repository.findByCode(code.value()).map(certificate -> viewOf(certificate, now));
    }

    private PublicCertificateView viewOf(ExamRegistrationCertificate certificate, LocalDateTime now) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        PublicCertificateStatus status = statusOf(certificate, now);
        return new PublicCertificateView(
                PublicCertificateDocumentKind.EXAM_REGISTRATION,
                status,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                snapshot.titleKind(),
                snapshot.titleName(),
                snapshot.graduationDate(),
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                status == PublicCertificateStatus.REVOKED ? certificate.getRevokedAt() : null,
                certificate.getRegistryNumber(),
                null);
    }

    private PublicCertificateStatus statusOf(ExamRegistrationCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == DocumentValidityStatus.REVOKED) {
            return PublicCertificateStatus.REVOKED;
        }
        if (certificate.getStatus() != DocumentValidityStatus.VALID || !now.isBefore(certificate.getExpiresAt())) {
            return PublicCertificateStatus.EXPIRED;
        }
        return PublicCertificateStatus.VALID;
    }
}
