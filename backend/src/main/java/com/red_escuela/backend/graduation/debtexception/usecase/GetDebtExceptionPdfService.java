package com.red_escuela.backend.graduation.debtexception.usecase;

import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionPdf;
import com.red_escuela.backend.graduation.debtexception.specialist.DebtExceptionDownloader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDebtExceptionPdfService implements GetDebtExceptionPdfUseCase {

    private final DebtExceptionDownloader downloader;

    public GetDebtExceptionPdfService(DebtExceptionDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public DebtExceptionPdf pdfOf(Long id) {
        return downloader.download(id);
    }
}
