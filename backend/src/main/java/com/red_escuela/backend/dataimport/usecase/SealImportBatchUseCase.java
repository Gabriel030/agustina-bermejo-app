package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;

public interface SealImportBatchUseCase {
    ImportBatchResponse seal(Long batchId, String confirmation);
}
