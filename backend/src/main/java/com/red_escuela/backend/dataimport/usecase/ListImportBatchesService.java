package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListImportBatchesService implements ListImportBatchesUseCase {
    private final ImportBatchReader batchReader;

    public ListImportBatchesService(ImportBatchReader batchReader) {
        this.batchReader = batchReader;
    }

    @Override
    public List<ImportBatchResponse> list() {
        return batchReader.list();
    }
}
