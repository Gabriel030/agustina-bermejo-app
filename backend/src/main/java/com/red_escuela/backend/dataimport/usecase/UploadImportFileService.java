package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.dataimport.specialist.ImportFileStager;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UploadImportFileService implements UploadImportFileUseCase {
    private final ImportFileStager fileStager;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver currentActorFinder;

    public UploadImportFileService(ImportFileStager fileStager, ImportBatchReader batchReader, ImportActorResolver currentActorFinder) {
        this.fileStager = fileStager;
        this.batchReader = batchReader;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ImportBatchResponse upload(UploadImportFileCommand command) {
        fileStager.stage(command.batchId(), currentActorFinder.currentPersonId(), command.entityType(),
                command.originalName(), command.content());
        return batchReader.get(command.batchId());
    }
}
