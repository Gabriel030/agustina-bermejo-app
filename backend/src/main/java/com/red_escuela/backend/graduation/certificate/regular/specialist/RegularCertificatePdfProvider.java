package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class RegularCertificatePdfProvider {

    private static final String DOCUMENT_TYPE = "REGULAR_CERTIFICATE";

    private final RegularStudentCertificateRepository repository;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final RegularCertificateTemplate template;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;

    public RegularCertificatePdfProvider(RegularStudentCertificateRepository repository, CertificateFileStore fileStore,
                                         CertificatePdfRenderer renderer, RegularCertificateTemplate template,
                                         CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                         CertificateDocumentAuditRecorder auditRecorder) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.template = template;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public RegularCertificatePdf pdfOf(Long id) {
        RegularStudentCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new RegularCertificateNotFoundException());
        Optional<byte[]> stored = fileStore.load(certificate.getPdfPath())
                .filter(content -> digest.sha256(content).equals(certificate.getPdfSha256()));
        if (stored.isPresent()) {
            recordDownload(certificate);
            return new RegularCertificatePdf(certificate.getCode(), stored.get());
        }
        log.warn("El PDF de la constancia {} falta o no coincide con su hash; se regenera desde el snapshot", certificate.getId());
        CertificateCode code = certificate.certificateCode();
        byte[] regenerated = renderer.render(template.definitionFor(certificate.getSnapshot(), certificate.getExpiresAt().toLocalDate()),
                certificate.getSnapshot(), code, verificationUrl.of(code));
        String regeneratedSha256 = digest.sha256(regenerated);
        if (certificate.isPdfRegenerationVerifiable() && !regeneratedSha256.equals(certificate.getPdfSha256())) {
            log.error("El PDF regenerado de la constancia {} no coincide con el hash registrado", certificate.getId());
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), regeneratedSha256,
                    certificate.getRenderEngineVersion());
            throw new CertificatePdfIntegrityMismatchException();
        }
        certificate.replacePdf(regeneratedSha256, fileStore.save(code.value(), regenerated));
        recordDownload(certificate);
        return new RegularCertificatePdf(certificate.getCode(), regenerated);
    }

    private void recordDownload(RegularStudentCertificate certificate) {
        auditRecorder.downloaded(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), certificate.getRenderEngineVersion());
    }
}
