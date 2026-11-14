package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;

public interface RollbackImportBatchUseCase {
    ImportBatchResponse rollback(Long batchId, String confirmation);
}
