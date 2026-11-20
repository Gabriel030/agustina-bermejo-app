package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportRowErrorResponse;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListImportRowErrorsService implements ListImportRowErrorsUseCase {
    private final ImportBatchReader batchReader;

    public ListImportRowErrorsService(ImportBatchReader batchReader) {
        this.batchReader = batchReader;
    }

    @Override
    public PageResponse<ImportRowErrorResponse> list(Long batchId, int page, int size) {
        return batchReader.errors(batchId, page, size);
    }
}
