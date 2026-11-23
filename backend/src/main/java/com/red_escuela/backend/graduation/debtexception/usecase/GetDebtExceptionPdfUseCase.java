package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;

public interface GetDebtExceptionPdfUseCase {

    ProcedureDocumentPdf pdfOf(Long id);
}
