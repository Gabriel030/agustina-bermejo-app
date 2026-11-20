package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportRowErrorResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListImportRowErrorsUseCase {
    PageResponse<ImportRowErrorResponse> list(Long batchId, int page, int size);
}
