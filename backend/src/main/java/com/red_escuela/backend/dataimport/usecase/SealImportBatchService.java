package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchLifecycleRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.dataimport.specialist.ImportConfirmation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SealImportBatchService implements SealImportBatchUseCase {
    private final ImportBatchLifecycleRecorder lifecycleRecorder;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver actorResolver;

    public SealImportBatchService(ImportBatchLifecycleRecorder lifecycleRecorder, ImportBatchReader batchReader,
            ImportActorResolver actorResolver) {
        this.lifecycleRecorder = lifecycleRecorder;
        this.batchReader = batchReader;
        this.actorResolver = actorResolver;
    }

    @Override
    public ImportBatchResponse seal(Long batchId, String confirmation) {
        ImportConfirmation.require(confirmation, ImportConfirmation.SEAL_WORD);
        lifecycleRecorder.seal(batchId, actorResolver.currentPersonId());
        return batchReader.get(batchId);
    }
}
