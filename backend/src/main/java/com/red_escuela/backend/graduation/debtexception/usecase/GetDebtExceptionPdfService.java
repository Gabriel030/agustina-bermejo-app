package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionAccessGuard;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDebtExceptionPdfService implements GetDebtExceptionPdfUseCase {

    private final DebtExceptionAccessGuard accessGuard;

    public GetDebtExceptionPdfService(DebtExceptionAccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @Override
    public ProcedureDocumentPdf pdfOf(Long id) {
        accessGuard.requireNotSubdirector();
        throw new DocumentOperationPendingException();
    }
}
