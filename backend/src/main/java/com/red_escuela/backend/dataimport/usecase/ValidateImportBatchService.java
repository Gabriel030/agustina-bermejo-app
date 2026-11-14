package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.dataimport.specialist.ImportBatchValidator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ValidateImportBatchService implements ValidateImportBatchUseCase {
    private final ImportBatchValidator batchValidator;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver currentActorFinder;

    public ValidateImportBatchService(ImportBatchValidator batchValidator, ImportBatchReader batchReader, ImportActorResolver currentActorFinder) {
        this.batchValidator = batchValidator;
        this.batchReader = batchReader;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ImportBatchResponse validate(Long batchId) {
        batchValidator.validate(batchId, currentActorFinder.currentPersonId());
        return batchReader.get(batchId);
    }
}
