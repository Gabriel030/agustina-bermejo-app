package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateRevokedException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificatePdfProvider;
import com.red_escuela.backend.graduation.procedure.dto.ApprovedProcedureResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GraduationApprovedProcedureSource implements ApprovedProcedureSource {

    private static final String TITLE = "Certificado de egreso";

    private final GraduationCertificateRepository repository;
    private final GraduationCertificatePdfProvider pdfProvider;

    public GraduationApprovedProcedureSource(GraduationCertificateRepository repository, GraduationCertificatePdfProvider pdfProvider) {
        this.repository = repository;
        this.pdfProvider = pdfProvider;
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
    @Transactional
    public ProcedureDocumentPdf pdfOf(Long id, Long studentId) {
        GraduationCertificate certificate = repository.findById(id)
                .filter(found -> found.getStudentId().equals(studentId))
                .orElseThrow(() -> new GraduationCertificateNotFoundException(id));
        if (!certificate.isRevocable()) {
            throw new GraduationCertificateRevokedException();
        }
        GraduationCertificatePdf pdf = pdfProvider.pdfOf(id);
        return new ProcedureDocumentPdf(pdf.code(), pdf.content());
    }

    private static ApprovedProcedureResponse toResponse(GraduationCertificate certificate) {
        DocumentValidityStatus status = certificate.isRevocable() ? DocumentValidityStatus.VALID : DocumentValidityStatus.REVOKED;
        return new ApprovedProcedureResponse(CertificateDocumentType.GRADUATION_CERTIFICATE, certificate.getId(), TITLE,
                certificate.getSnapshot().titleName(), certificate.getIssuedAt(), null, status, DocumentOrigin.ISSUED_BY_STAFF,
                status == DocumentValidityStatus.VALID);
    }
}
