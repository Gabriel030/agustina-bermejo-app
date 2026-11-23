package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionPdf;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDebtExceptionPdfService implements GetDebtExceptionPdfUseCase {

    @Override
    public DebtExceptionPdf pdfOf(Long id) {
        throw new DocumentOperationPendingException();
    }
}
