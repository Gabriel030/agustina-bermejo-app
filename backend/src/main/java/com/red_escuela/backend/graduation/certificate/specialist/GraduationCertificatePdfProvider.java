package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.BuiltInCertificateTemplate;
import com.red_escuela.backend.graduation.certificate.engine.CertificateAssets;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GraduationCertificatePdfProvider {

    private static final String DOCUMENT_TYPE = "GRADUATION_CERTIFICATE";

    private static final Logger log = LoggerFactory.getLogger(GraduationCertificatePdfProvider.class);

    private final GraduationCertificateRepository repository;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final BuiltInCertificateTemplate builtInTemplate;
    private final CertificateTemplateRenderSource templateSource;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;

    public GraduationCertificatePdfProvider(GraduationCertificateRepository repository, CertificateFileStore fileStore,
                                            CertificatePdfRenderer renderer, BuiltInCertificateTemplate builtInTemplate, CertificateTemplateRenderSource templateSource,
                                            CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                            CertificateDocumentAuditRecorder auditRecorder) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.builtInTemplate = builtInTemplate;
        this.templateSource = templateSource;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
    }

    @Transactional
    public GraduationCertificatePdf pdfOf(Long id) {
        GraduationCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new GraduationCertificateNotFoundException(id));
        Optional<byte[]> stored = storedContent(certificate);
        if (stored.isPresent()) {
            recordDownload(certificate);
            return new GraduationCertificatePdf(certificate.getCode(), stored.get());
        }
        log.warn("El PDF del certificado {} falta o no coincide con su hash; se regenera desde el snapshot", certificate.getId());
        byte[] regenerated = regenerate(certificate);
        String regeneratedSha256 = digest.sha256(regenerated);
        if (certificate.isPdfRegenerationVerifiable() && !regeneratedSha256.equals(certificate.getPdfSha256())) {
            log.error("El PDF regenerado del certificado {} no coincide con el hash registrado", certificate.getId());
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), regeneratedSha256,
                    certificate.getRenderEngineVersion());
            throw new CertificatePdfIntegrityMismatchException();
        }
        certificate.replacePdf(regeneratedSha256, fileStore.save(certificate.getCode(), regenerated));
        recordDownload(certificate);
        return new GraduationCertificatePdf(certificate.getCode(), regenerated);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GraduationCertificatePdf> verifiedPdfOf(GraduationCertificate certificate) {
        Optional<byte[]> stored = storedContent(certificate);
        if (stored.isPresent()) {
            return Optional.of(new GraduationCertificatePdf(certificate.getCode(), stored.get()));
        }
        log.warn("El PDF del certificado {} falta o no coincide con su hash; se regenera desde el snapshot", certificate.getId());
        byte[] regenerated = regenerate(certificate);
        if (!digest.sha256(regenerated).equals(certificate.getPdfSha256())) {
            log.error("El PDF regenerado del certificado {} no coincide con el hash guardado; no se sirve", certificate.getId());
            return Optional.empty();
        }
        certificate.replacePdf(certificate.getPdfSha256(), fileStore.save(certificate.getCode(), regenerated));
        return Optional.of(new GraduationCertificatePdf(certificate.getCode(), regenerated));
    }

    private void recordDownload(GraduationCertificate certificate) {
        auditRecorder.downloaded(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), certificate.getRenderEngineVersion());
    }

    private Optional<byte[]> storedContent(GraduationCertificate certificate) {
        return fileStore.load(certificate.getPdfPath())
                .filter(content -> digest.sha256(content).equals(certificate.getPdfSha256()));
    }

    private byte[] regenerate(GraduationCertificate certificate) {
        CertificateCode code = certificate.certificateCode();
        CertificateAssets assets = certificate.getTemplateVersionId() == null
                ? new CertificateAssets(null, null, certificate.getRegistryNumber(), null)
                : templateSource.assetsOf(templateSource.byId(certificate.getTemplateVersionId()), certificate.getRegistryNumber());
        return renderer.render(builtInTemplate.resolve(certificate.getKind(), null), certificate.getSnapshot(),
                code, verificationUrl.of(code), assets);
    }
}
