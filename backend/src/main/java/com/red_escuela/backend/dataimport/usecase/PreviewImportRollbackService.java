package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse;
import com.red_escuela.backend.dataimport.specialist.ImportRollbackPreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewImportRollbackService implements PreviewImportRollbackUseCase {
    private final ImportRollbackPreviewer previewer;

    public PreviewImportRollbackService(ImportRollbackPreviewer previewer) {
        this.previewer = previewer;
    }

    @Override
    public ImportRollbackPreviewResponse preview(Long batchId) {
        return previewer.preview(batchId);
    }
}
