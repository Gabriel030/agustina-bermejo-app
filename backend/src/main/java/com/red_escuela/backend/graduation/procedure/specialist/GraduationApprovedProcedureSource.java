package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.CertificatePdfIntegrityMismatchException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.OwnGraduationCertificateDownloader;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GraduationApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Certificado de egreso";

    private final GraduationCertificateRepository repository;
    private final OwnGraduationCertificateDownloader downloader;

    public GraduationApprovedProcedureSource(GraduationCertificateRepository repository, OwnGraduationCertificateDownloader downloader) {
        this.repository = repository;
        this.downloader = downloader;
    }

    @Override
    public CertificateDocumentType documentType() {
        return CertificateDocumentType.GRADUATION_CERTIFICATE;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovedProcedureResponse> listOf(Long studentId) {
        return repository.findByStudentIdOrderByIssuedAtDescIdDesc(studentId).stream()
                .map(GraduationApprovedProcedureSource::toResponse)
                .toList();
    }

    @Override
    public ProcedureDocumentPdf pdfOf(Long id, Long studentId) {
        GraduationCertificatePdf pdf = downloader.download(id).orElseThrow(CertificatePdfIntegrityMismatchException::new);
        return new ProcedureDocumentPdf(pdf.code(), pdf.content());
    }

    private static ApprovedProcedureResponse toResponse(GraduationCertificate certificate) {
        DocumentValidityStatus status = certificate.isRevocable() ? DocumentValidityStatus.VALID : DocumentValidityStatus.REVOKED;
        return new ApprovedProcedureResponse(CertificateDocumentType.GRADUATION_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().titleName(), certificate.getIssuedAt(), null, status, DocumentOrigin.ISSUED_BY_STAFF,
                status == DocumentValidityStatus.VALID);
    }
}
