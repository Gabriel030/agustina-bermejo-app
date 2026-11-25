package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.exception.ApprovedSubjectsCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ApprovedSubjectsCertificatePdfProvider {

    private static final String DOCUMENT_TYPE = CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE.name();

    private final ApprovedSubjectsCertificateRepository repository;
    private final ApprovedSubjectsPdfRenderer renderer;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;
    private final Clock clock;

    public ApprovedSubjectsCertificatePdfProvider(ApprovedSubjectsCertificateRepository repository, ApprovedSubjectsPdfRenderer renderer,
                                                  CertificateDigest digest, CertificateDocumentAuditRecorder auditRecorder, Clock clock) {
        this.repository = repository;
        this.renderer = renderer;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public ApprovedSubjectsCertificatePdf pdfOfOwn(Long id, Long studentId) {
        ApprovedSubjectsCertificate certificate = repository.findByIdAndStudentId(id, studentId)
                .filter(found -> found.isRevocable() && !found.isExpiredAt(LocalDateTime.now(clock)))
                .orElseThrow(ApprovedSubjectsCertificateNotFoundException::new);
        byte[] regenerated = renderer.render(certificate.getSnapshot(), certificate.certificateCode());
        String regeneratedSha256 = digest.sha256(regenerated);
        if (!regeneratedSha256.equals(certificate.getPdfSha256())) {
            log.error("El PDF regenerado de la constancia de materias aprobadas {} no coincide con el hash registrado", certificate.getId());
            auditRecorder.hashMismatch(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), regeneratedSha256,
                    certificate.getRenderEngineVersion());
            throw new CertificatePdfIntegrityMismatchException();
        }
        auditRecorder.downloaded(DOCUMENT_TYPE, certificate.getId(), certificate.getPdfSha256(), certificate.getRenderEngineVersion());
        return new ApprovedSubjectsCertificatePdf(certificate.getCode(), regenerated);
    }
}
