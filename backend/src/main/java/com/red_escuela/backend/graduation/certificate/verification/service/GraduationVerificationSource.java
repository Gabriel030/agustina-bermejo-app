package com.red_escuela.backend.graduation.certificate.verification.service;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateDocumentKind;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class GraduationVerificationSource implements CertificateVerificationSource {

    private final GraduationCertificateRepository repository;

    public GraduationVerificationSource(GraduationCertificateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicCertificateView> findByCode(CertificateCode code, LocalDateTime now) {
        return repository.findByCode(code.value()).map(this::viewOf);
    }

    private PublicCertificateView viewOf(GraduationCertificate certificate) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        boolean revoked = certificate.getStatus() == GraduationCertificateStatus.REVOKED;
        return new PublicCertificateView(
                PublicCertificateDocumentKind.GRADUATION,
                revoked ? PublicCertificateStatus.REVOKED : PublicCertificateStatus.VALID,
                snapshot.holderFullName(),
                DocumentMasker.mask(snapshot.documentNumber()),
                snapshot.titleKind(),
                snapshot.titleName(),
                snapshot.graduationDate(),
                certificate.getIssuedAt(),
                null,
                revoked ? certificate.getRevokedAt() : null,
                certificate.getRegistryNumber(),
                snapshot.hours());
    }
}
