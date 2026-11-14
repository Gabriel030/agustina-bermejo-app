package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse;

public interface PreviewImportRollbackUseCase {
    ImportRollbackPreviewResponse preview(Long batchId);
}
