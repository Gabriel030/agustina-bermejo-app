package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.entity.ImportBatchStatus;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchLifecycleRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.dataimport.specialist.ImportConfirmation;
import com.red_escuela.backend.dataimport.specialist.ImportRollbackTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RollbackImportBatchService implements RollbackImportBatchUseCase {
    private final ImportBatchLifecycleRecorder lifecycleRecorder;
    private final ImportRollbackTransaction rollbackTransaction;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver actorResolver;

    public RollbackImportBatchService(ImportBatchLifecycleRecorder lifecycleRecorder,
            ImportRollbackTransaction rollbackTransaction, ImportBatchReader batchReader,
            ImportActorResolver actorResolver) {
        this.lifecycleRecorder = lifecycleRecorder;
        this.rollbackTransaction = rollbackTransaction;
        this.batchReader = batchReader;
        this.actorResolver = actorResolver;
    }

    @Override
    public ImportBatchResponse rollback(Long batchId, String confirmation) {
        ImportConfirmation.require(confirmation, ImportConfirmation.ROLLBACK_WORD);
        Long actorPersonId = actorResolver.currentPersonId();
        ImportBatchStatus previous = lifecycleRecorder.startRollback(batchId);
        try {
            rollbackTransaction.execute(batchId, actorPersonId);
        } catch (RuntimeException e) {
            lifecycleRecorder.abortRollback(batchId, previous, actorPersonId, e);
            throw e;
        }
        return batchReader.get(batchId);
    }
}
