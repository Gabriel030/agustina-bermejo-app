package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.StudentRegularCertificateDownloader;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RegularApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Constancia de alumno regular";

    private final RegularStudentCertificateRepository repository;
    private final StudentRegularCertificateDownloader downloader;
    private final Clock clock;

    public RegularApprovedProcedureSource(RegularStudentCertificateRepository repository,
                                          StudentRegularCertificateDownloader downloader, Clock clock) {
        this.repository = repository;
        this.downloader = downloader;
        this.clock = clock;
    }

    @Override
    public CertificateDocumentType documentType() {
        return CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE;
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
        RegularCertificatePdf pdf = downloader.pdfOf(id, studentId);
        return new ProcedureDocumentPdf(pdf.code(), pdf.content());
    }

    private static ApprovedProcedureResponse toResponse(RegularStudentCertificate certificate, LocalDateTime now) {
        DocumentValidityStatus status = statusOf(certificate, now);
        DocumentOrigin origin = certificate.getIssueChannel() == RegularCertificateChannel.STUDENT_PORTAL
                ? DocumentOrigin.SELF_SERVICE : DocumentOrigin.ISSUED_BY_STAFF;
        return new ApprovedProcedureResponse(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().titleName(), certificate.getIssuedAt(), certificate.getExpiresAt(), status, origin,
                status == DocumentValidityStatus.VALID);
    }

    private static DocumentValidityStatus statusOf(RegularStudentCertificate certificate, LocalDateTime now) {
        if (certificate.getStatus() == RegularCertificateStatus.REVOKED) {
            return DocumentValidityStatus.REVOKED;
        }
        if (certificate.getStatus() == RegularCertificateStatus.EXPIRED || certificate.isExpiredAt(now)) {
            return DocumentValidityStatus.EXPIRED;
        }
        return DocumentValidityStatus.VALID;
    }
}
