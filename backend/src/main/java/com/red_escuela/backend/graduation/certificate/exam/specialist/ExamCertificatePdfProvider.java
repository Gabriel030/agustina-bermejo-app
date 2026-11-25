package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificatePdfProvider {

    private static final String DOCUMENT_TYPE = "EXAM_CERTIFICATE";

    private final ExamCertificateRepository repository;
    private final ExamBoardAttendanceReader boardReader;
    private final CertificateFileStore fileStore;
    private final CertificatePdfRenderer renderer;
    private final ExamCertificateTemplate template;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;
    private final CertificateTemplateRenderSource templateSource;
    private final Clock clock;

    public ExamCertificatePdfProvider(ExamCertificateRepository repository, ExamBoardAttendanceReader boardReader,
                                      CertificateFileStore fileStore, CertificatePdfRenderer renderer,
                                      ExamCertificateTemplate template, CertificateVerificationUrl verificationUrl,
                                      CertificateDigest digest, CertificateDocumentAuditRecorder auditRecorder,
                                      CertificateTemplateRenderSource templateSource, Clock clock) {
        this.clock = clock;
        this.repository = repository;
        this.boardReader = boardReader;
        this.fileStore = fileStore;
        this.renderer = renderer;
        this.template = template;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
        this.templateSource = templateSource;
    }

    @Transactional
    public GraduationCertificatePdf pdfOf(Long id) {
        ExamCertificate certificate = repository.findById(id).orElseThrow(ExamCertificateNotFoundException::new);
        boardReader.requireAccess(certificate.getExamBoardId());
        return pdfOf(certificate);
    }

    @Transactional
    public GraduationCertificatePdf pdfOfOwn(Long id, Long studentId) {
        ExamCertificate certificate = repository.findByIdAndStudentId(id, studentId)
                .filter(found -> found.isValidAt(LocalDateTime.now(clock)))
                .orElseThrow(ExamCertificateNotFoundException::new);
        return pdfOf(certificate);
    }

    private GraduationCertificatePdf pdfOf(ExamCertificate certificate) {
        Optional<byte[]> stored = fileStore.load(certificate.getPdfPath())
                .filter(content -> digest.sha256(content).equals(certificate.getPdfSha256()));
        if (stored.isPresent()) {
            recordDownload(certificate);
            return new GraduationCertificatePdf(certificate.getCode(), stored.get());
        }
        CertificateCode code = certificate.certificateCode();
        CertificateTemplateDefinition definition = template.definitionFor(certificate.getSnapshot(), certificate.getExpiresAt().toLocalDate(),
                certificate.getRecipientType(), certificate.getRecipientText());
        byte[] regenerated = certificate.getTemplateVersionId() == null
                ? renderer.render(definition, certificate.getSnapshot(), code, verificationUrl.of(code))
                : renderer.render(definition, certificate.getSnapshot(), code, verificationUrl.of(code),
                        templateSource.assetsOf(templateSource.byId(certificate.getTemplateVersionId()), certificate.getRegistryNumber()));
        String regeneratedSha256 = digest.sha256(regenerated);
        if (certificate.isPdfRegenerationVerifiable() && !regeneratedSha256.equals(certificate.getPdfSha256())) {
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), regeneratedSha256,
                    certificate.getRenderEngineVersion());
            throw new CertificatePdfIntegrityMismatchException();
        }
        certificate.replacePdf(regeneratedSha256, fileStore.save(code.value(), regenerated));
        recordDownload(certificate);
        return new GraduationCertificatePdf(certificate.getCode(), regenerated);
    }

    private void recordDownload(ExamCertificate certificate) {
        auditRecorder.downloaded(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), certificate.getRenderEngineVersion());
    }
}
