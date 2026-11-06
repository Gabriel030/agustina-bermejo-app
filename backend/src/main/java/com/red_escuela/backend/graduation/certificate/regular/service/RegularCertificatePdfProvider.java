package com.red_escuela.backend.graduation.certificate.regular.service;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.service.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.service.CertificateVerificationUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class RegularCertificatePdfProvider {

    private final RegularStudentCertificateRepository repository;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final RegularCertificateTemplate template;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;

    public RegularCertificatePdfProvider(RegularStudentCertificateRepository repository, CertificateFileStore fileStore,
                                         CertificatePdfRenderer renderer, RegularCertificateTemplate template,
                                         CertificateVerificationUrl verificationUrl, CertificateDigest digest) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.template = template;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
    }

    @Transactional
    public RegularCertificatePdf pdfOf(Long id) {
        RegularStudentCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new RegularCertificateNotFoundException());
        Optional<byte[]> stored = fileStore.load(certificate.getPdfPath())
                .filter(content -> digest.sha256(content).equals(certificate.getPdfSha256()));
        if (stored.isPresent()) {
            return new RegularCertificatePdf(certificate.getCode(), stored.get());
        }
        log.warn("El PDF de la constancia {} falta o no coincide con su hash; se regenera desde el snapshot", certificate.getCode());
        CertificateCode code = certificate.certificateCode();
        byte[] regenerated = renderer.render(template.definitionFor(certificate.getSnapshot(), certificate.getExpiresAt().toLocalDate()),
                certificate.getSnapshot(), code, verificationUrl.of(code));
        certificate.replacePdf(digest.sha256(regenerated), fileStore.save(code.value(), regenerated));
        return new RegularCertificatePdf(certificate.getCode(), regenerated);
    }
}
