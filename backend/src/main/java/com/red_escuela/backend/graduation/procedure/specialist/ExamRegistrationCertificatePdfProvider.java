package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.exception.ExamRegistrationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class ExamRegistrationCertificatePdfProvider {

    private static final String DOCUMENT_TYPE = CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE.name();

    private final ExamRegistrationCertificateRepository repository;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final ExamRegistrationCertificateTemplate template;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;
    private final Clock clock;

    public ExamRegistrationCertificatePdfProvider(ExamRegistrationCertificateRepository repository, CertificateFileStore fileStore,
                                                  CertificatePdfRenderer renderer, ExamRegistrationCertificateTemplate template,
                                                  CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                                  CertificateDocumentAuditRecorder auditRecorder,
                                                  Clock clock) {
        this.repository = repository;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.template = template;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public ProcedureDocumentPdf pdfOfOwn(Long id, Long studentId) {
        ExamRegistrationCertificate certificate = repository.findByIdAndStudentId(id, studentId)
                .filter(found -> found.isValidAt(LocalDateTime.now(clock)))
                .orElseThrow(ExamRegistrationCertificateNotFoundException::new);
        Optional<byte[]> loaded = fileStore.load(certificate.getPdfPath());
        if (loaded.isPresent() && digest.sha256(loaded.get()).equals(certificate.getPdfSha256())) {
            recordDownload(certificate);
            return new ProcedureDocumentPdf(certificate.getCode(), loaded.get());
        }
        if (loaded.isPresent()) {
            log.error("El PDF almacenado de la constancia de inscripción {} no coincide con su hash; se regenera desde el snapshot",
                    certificate.getId());
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(),
                    digest.sha256(loaded.get()), certificate.getRenderEngineVersion());
        } else {
            log.warn("El PDF de la constancia de inscripción {} falta; se regenera desde el snapshot", certificate.getId());
        }
        CertificateCode code = certificate.certificateCode();
        byte[] regenerated = renderer.render(template.definitionFor(certificate.getSnapshot(), certificate.getRecipientType(),
                certificate.getRecipientText()), certificate.getSnapshot(), code, verificationUrl.of(code));
        String regeneratedSha256 = digest.sha256(regenerated);
        if (!regeneratedSha256.equals(certificate.getPdfSha256())) {
            log.error("El PDF regenerado de la constancia de inscripción {} no coincide con el hash registrado", certificate.getId());
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), regeneratedSha256,
                    certificate.getRenderEngineVersion());
            throw new CertificatePdfIntegrityMismatchException();
        }
        certificate.replacePdfPath(fileStore.save(code.value(), regenerated));
        recordDownload(certificate);
        return new ProcedureDocumentPdf(certificate.getCode(), regenerated);
    }

    private void recordDownload(ExamRegistrationCertificate certificate) {
        auditRecorder.downloaded(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), certificate.getRenderEngineVersion());
    }
}
