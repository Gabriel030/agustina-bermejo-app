package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.dto.ImportFileDownload;
import com.red_escuela.backend.dataimport.specialist.ImportTemplateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DownloadImportTemplateService implements DownloadImportTemplateUseCase {
    private final ImportTemplateReader templateReader;

    public DownloadImportTemplateService(ImportTemplateReader templateReader) {
        this.templateReader = templateReader;
    }

    @Override
    public ImportFileDownload download(ImportEntityType entityType) {
        return templateReader.read(entityType);
    }
}
