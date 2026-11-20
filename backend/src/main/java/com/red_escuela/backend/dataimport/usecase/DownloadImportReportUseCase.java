package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportFileDownload;

public interface DownloadImportReportUseCase {
    ImportFileDownload download(Long batchId);
}
