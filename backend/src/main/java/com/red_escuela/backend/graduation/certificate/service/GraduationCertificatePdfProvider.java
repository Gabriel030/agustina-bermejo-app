package com.red_escuela.backend.graduation.certificate.service;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateResolver;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GraduationCertificatePdfProvider {

    private static final Logger log = LoggerFactory.getLogger(GraduationCertificatePdfProvider.class);

    private final GraduationCertificateRepository repository;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final CertificateTemplateResolver templateResolver;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;

    public GraduationCertificatePdfProvider(GraduationCertificateRepository repository, CertificateFileStore fileStore,
                                            CertificatePdfRenderer renderer, CertificateTemplateResolver templateResolver,
                                            CertificateVerificationUrl verificationUrl, CertificateDigest digest) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.templateResolver = templateResolver;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
    }

    @Transactional
    public GraduationCertificatePdf pdfOf(Long id) {
        GraduationCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new GraduationCertificateNotFoundException(id));
        Optional<byte[]> stored = fileStore.load(certificate.getPdfPath())
                .filter(content -> digest.sha256(content).equals(certificate.getPdfSha256()));
        if (stored.isPresent()) {
            return new GraduationCertificatePdf(certificate.getCode(), stored.get());
        }
        log.warn("El PDF del certificado {} falta o no coincide con su hash; se regenera desde el snapshot", certificate.getId());
        CertificateCode code = certificate.certificateCode();
        byte[] regenerated = renderer.render(templateResolver.resolve(certificate.getKind(), null), certificate.getSnapshot(),
                code, verificationUrl.of(code));
        String path = fileStore.save(code.value(), regenerated);
        certificate.replacePdf(digest.sha256(regenerated), path);
        return new GraduationCertificatePdf(certificate.getCode(), regenerated);
    }
}
