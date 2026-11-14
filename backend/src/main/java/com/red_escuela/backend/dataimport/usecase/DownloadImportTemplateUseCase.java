package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.dto.ImportFileDownload;

public interface DownloadImportTemplateUseCase {
    ImportFileDownload download(ImportEntityType entityType);
}
