package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.examregistration.specialist.ExamRegistrationCertificatePdfProvider;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetApprovedProcedurePdfService implements GetApprovedProcedurePdfUseCase {

    private final ExamRegistrationCertificatePdfProvider examRegistrationPdfProvider;

    public GetApprovedProcedurePdfService(ExamRegistrationCertificatePdfProvider examRegistrationPdfProvider) {
        this.examRegistrationPdfProvider = examRegistrationPdfProvider;
    }

    @Override
    public ProcedureDocumentPdf pdfOf(CertificateDocumentType documentType, Long id) {
        if (documentType == CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE) {
            return examRegistrationPdfProvider.pdfOfOwn(id);
        }
        throw new DocumentOperationPendingException();
    }
}
