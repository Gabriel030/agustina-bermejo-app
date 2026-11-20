package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;

public interface CreateImportBatchUseCase {
    ImportBatchResponse create(String name);
}
