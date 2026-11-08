package com.red_escuela.backend.graduation.certificate.verification.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateDocumentKind;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class RegularVerificationSource implements CertificateVerificationSource {

    private final RegularStudentCertificateRepository repository;

    public RegularVerificationSource(RegularStudentCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicCertificateView> findByCode(CertificateCode code, LocalDateTime now) {
        return repository.findByCode(code.value()).map(certificate -> viewOf(certificate, now));
    }

    private PublicCertificateView viewOf(RegularStudentCertificate certificate, LocalDateTime now) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        PublicCertificateStatus status = statusOf(certificate, now);
        return new PublicCertificateView(
                PublicCertificateDocumentKind.REGULAR,
                status,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                snapshot.titleKind(),
                snapshot.titleName(),
                null,
                certificate.getIssuedAt(),
                certificate.getExpiresAt(),
                status == PublicCertificateStatus.REVOKED ? certificate.getRevokedAt() : null,
                certificate.getRegistryNumber(),
                snapshot.hours());
    }

    private PublicCertificateStatus statusOf(RegularStudentCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == RegularCertificateStatus.REVOKED) {
            return PublicCertificateStatus.REVOKED;
        }
        if (certificate.getStatus() == RegularCertificateStatus.EXPIRED || certificate.getExpiresAt().isBefore(now)) {
            return PublicCertificateStatus.EXPIRED;
        }
        return PublicCertificateStatus.VALID;
    }
}
