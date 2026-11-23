package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDebtExceptionPdfService implements GetDebtExceptionPdfUseCase {

    @Override
    public ProcedureDocumentPdf pdfOf(Long id) {
        throw new DocumentOperationPendingException();
    }
}
