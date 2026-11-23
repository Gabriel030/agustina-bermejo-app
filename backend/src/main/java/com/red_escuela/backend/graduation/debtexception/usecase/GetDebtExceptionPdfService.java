package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.procedure.dto.ProcedureDocumentPdf;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionAccessGuard;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionDownloader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDebtExceptionPdfService implements GetDebtExceptionPdfUseCase {

    private final DebtExceptionAccessGuard accessGuard;

    private final DebtExceptionDownloader downloader;

    public GetDebtExceptionPdfService(DebtExceptionAccessGuard accessGuard, DebtExceptionDownloader downloader) {
        this.accessGuard = accessGuard;
        this.downloader = downloader;
    }

    @Override
    public ProcedureDocumentPdf pdfOf(Long id) {
        accessGuard.requireNotSubdirector();
        return downloader.download(id);
    }
}
