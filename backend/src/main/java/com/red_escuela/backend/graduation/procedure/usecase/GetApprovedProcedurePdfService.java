package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetApprovedProcedurePdfService implements GetApprovedProcedurePdfUseCase {

    @Override
    public ProcedureDocumentPdf pdfOf(CertificateDocumentType documentType, Long id) {
        throw new DocumentOperationPendingException();
    }
}
