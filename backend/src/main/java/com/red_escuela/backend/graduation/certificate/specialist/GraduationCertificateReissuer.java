package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationCertificateReissuer {

    private final GraduationCertificateRevoker revoker;
    private final GraduationCertificateIssuer issuer;
    private final GraduationCertificateRepository repository;

    public GraduationCertificateReissuer(GraduationCertificateRevoker revoker, GraduationCertificateIssuer issuer,
                                         GraduationCertificateRepository repository) {
        this.revoker = revoker;
        this.issuer = issuer;
        this.repository = repository;
    }

    @Transactional
    public Long reissue(Long certificateId, String reason) {
        GraduationCertificate revoked = revoker.revokeLocked(certificateId, reason);
        GraduationCertificateResponse issued = issuer.issue(revoked.getKind(), revoked.enrollmentId());
        GraduationCertificate replacement = repository.findById(issued.id()).orElseThrow();
        replacement.markAsReplacing(revoked.getId());
        repository.saveAndFlush(replacement);
        return replacement.getId();
    }
}
