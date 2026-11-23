package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionPdf;

public interface GetDebtExceptionPdfUseCase {

    DebtExceptionPdf pdfOf(Long id);
}
