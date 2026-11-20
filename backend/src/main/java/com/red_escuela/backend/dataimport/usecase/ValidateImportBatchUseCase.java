package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;

public interface ValidateImportBatchUseCase {
    ImportBatchResponse validate(Long batchId);
}
