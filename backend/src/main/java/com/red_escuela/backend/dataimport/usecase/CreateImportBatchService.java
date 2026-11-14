package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchCreator;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateImportBatchService implements CreateImportBatchUseCase {
    private final ImportBatchCreator batchCreator;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver currentActorFinder;

    public CreateImportBatchService(ImportBatchCreator batchCreator, ImportBatchReader batchReader, ImportActorResolver currentActorFinder) {
        this.batchCreator = batchCreator;
        this.batchReader = batchReader;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ImportBatchResponse create(String name) {
        var batch = batchCreator.create(name, currentActorFinder.currentPersonId());
        return batchReader.get(batch.getId());
    }
}
