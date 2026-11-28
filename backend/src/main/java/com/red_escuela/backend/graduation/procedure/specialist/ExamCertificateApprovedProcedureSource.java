package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamCertificateApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Constancia de examen";

    private final ExamCertificateRepository repository;
    private final ExamCertificatePdfProvider pdfProvider;
    private final Clock clock;

    public ExamCertificateApprovedProcedureSource(ExamCertificateRepository repository, ExamCertificatePdfProvider pdfProvider,
                                                  Clock clock) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
        this.clock = clock;
    }

    @Override
    public CertificateDocumentType documentType() {
        return CertificateDocumentType.EXAM_CERTIFICATE;
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
        GraduationCertificatePdf pdf = pdfProvider.pdfOfOwn(id, studentId);
        return new ProcedureDocumentPdf(pdf.code(), pdf.content());
    }

    private static ApprovedProcedureResponse toResponse(ExamCertificate certificate, LocalDateTime now) {
        DocumentValidityStatus status = certificate.isExpiredAt(now) ? DocumentValidityStatus.EXPIRED : certificate.getStatus();
        return new ApprovedProcedureResponse(CertificateDocumentType.EXAM_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().titleName(), certificate.getIssuedAt(), certificate.getExpiresAt(), status,
                certificate.getOrigin(), status == DocumentValidityStatus.VALID);
    }
}
