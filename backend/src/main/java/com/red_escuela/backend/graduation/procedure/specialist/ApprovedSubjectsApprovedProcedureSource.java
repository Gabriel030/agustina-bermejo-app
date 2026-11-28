package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExpiredException;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ApprovedSubjectsApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Constancia de materias aprobadas";

    private final ApprovedSubjectsCertificateRepository repository;
    private final ApprovedSubjectsCertificatePdfProvider pdfProvider;
    private final Clock clock;

    public ApprovedSubjectsApprovedProcedureSource(ApprovedSubjectsCertificateRepository repository,
                                                   ApprovedSubjectsCertificatePdfProvider pdfProvider, Clock clock) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
        this.clock = clock;
    }

    @Override
    public CertificateDocumentType documentType() {
        return CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovedProcedureResponse> listOf(Long studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return repository.findByStudentIdOrderByIssuedAtDesc(studentId).stream()
                .map(certificate -> toResponse(certificate, now))
                .toList();
    }

    @Override
    public ProcedureDocumentPdf pdfOf(Long id, Long studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean expired = repository.findByIdAndStudentId(id, studentId)
                .filter(found -> found.getStatus() == ApprovedSubjectsCertificateStatus.EXPIRED || found.isExpiredAt(now))
                .isPresent();
        if (expired) {
            throw new CertificateExpiredException();
        }
        ApprovedSubjectsCertificatePdf pdf = pdfProvider.pdfOfOwn(id, studentId);
        return new ProcedureDocumentPdf(pdf.code(), pdf.content());
    }

    private static ApprovedProcedureResponse toResponse(ApprovedSubjectsCertificate certificate, LocalDateTime now) {
        DocumentValidityStatus status = statusOf(certificate, now);
        return new ApprovedProcedureResponse(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().programName(), certificate.getIssuedAt(), certificate.getExpiresAt(), status,
                DocumentOrigin.SELF_SERVICE, status == DocumentValidityStatus.VALID);
    }

    private static DocumentValidityStatus statusOf(ApprovedSubjectsCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == ApprovedSubjectsCertificateStatus.REVOKED) {
            return DocumentValidityStatus.REVOKED;
        }
        if (certificate.getStatus() == ApprovedSubjectsCertificateStatus.EXPIRED || certificate.isExpiredAt(now)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
