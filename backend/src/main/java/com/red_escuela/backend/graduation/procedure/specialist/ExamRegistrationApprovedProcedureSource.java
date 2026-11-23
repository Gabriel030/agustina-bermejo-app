package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamRegistrationApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Constancia de inscripción a examen";

    private final ExamRegistrationCertificateRepository repository;
    private final ExamRegistrationCertificatePdfProvider pdfProvider;
    private final Clock clock;

    public ExamRegistrationApprovedProcedureSource(ExamRegistrationCertificateRepository repository,
                                                   ExamRegistrationCertificatePdfProvider pdfProvider, Clock clock) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
        this.clock = clock;
    }

    @Override
    public CertificateDocumentType documentType() {
        return CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE;
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
        return pdfProvider.pdfOfOwn(id, studentId);
    }

    private static ApprovedProcedureResponse toResponse(ExamRegistrationCertificate certificate, LocalDateTime now) {
        DocumentValidityStatus status = statusOf(certificate, now);
        return new ApprovedProcedureResponse(CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().titleName(), certificate.getIssuedAt(), certificate.getExpiresAt(), status,
                DocumentOrigin.SELF_SERVICE, status == DocumentValidityStatus.VALID);
    }

    private static DocumentValidityStatus statusOf(ExamRegistrationCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == DocumentValidityStatus.VALID && !certificate.isValidAt(now)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return certificate.getStatus();
    }
}
