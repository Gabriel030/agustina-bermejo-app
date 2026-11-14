package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetImportBatchService implements GetImportBatchUseCase {
    private final ImportBatchReader batchReader;

    public GetImportBatchService(ImportBatchReader batchReader) {
        this.batchReader = batchReader;
    }

    @Override
    public ImportBatchResponse get(Long batchId) {
        return batchReader.get(batchId);
    }
}
