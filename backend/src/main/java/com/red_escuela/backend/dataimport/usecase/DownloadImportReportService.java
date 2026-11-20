package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportFileDownload;
import com.red_escuela.backend.dataimport.specialist.ImportReportWriter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DownloadImportReportService implements DownloadImportReportUseCase {
    private final ImportReportWriter reportWriter;

    public DownloadImportReportService(ImportReportWriter reportWriter) {
        this.reportWriter = reportWriter;
    }

    @Override
    public ImportFileDownload download(Long batchId) {
        return reportWriter.write(batchId);
    }
}
