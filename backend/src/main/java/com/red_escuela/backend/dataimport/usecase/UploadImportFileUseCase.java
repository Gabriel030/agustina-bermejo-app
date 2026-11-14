package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;

public interface UploadImportFileUseCase {
    ImportBatchResponse upload(UploadImportFileCommand command);
}
