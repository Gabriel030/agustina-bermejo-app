package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.specialist.CommitImportChunkTransaction;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchProgressRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.dataimport.specialist.ImportDefinitionRegistry;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class CommitImportBatchService implements CommitImportBatchUseCase {
    private static final int CHUNK_SIZE = 200;
    private final ImportBatchProgressRecorder progressRecorder;
    private final CommitImportChunkTransaction chunkTransaction;
    private final ImportDefinitionRegistry registry;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver currentActorFinder;

    public CommitImportBatchService(ImportBatchProgressRecorder progressRecorder, CommitImportChunkTransaction chunkTransaction, ImportDefinitionRegistry registry, ImportBatchReader batchReader, ImportActorResolver currentActorFinder) {
        this.progressRecorder = progressRecorder;
        this.chunkTransaction = chunkTransaction;
        this.registry = registry;
        this.batchReader = batchReader;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ImportBatchResponse commit(Long batchId, boolean onlyValidRows) {
        Long actorPersonId = currentActorFinder.currentPersonId();
        Long institutionId = progressRecorder.startCommit(batchId, onlyValidRows);
        try {
            for (ImportEntityType type : registry.commitOrder()) {
                commitType(batchId, institutionId, type);
            }
        } finally {
            progressRecorder.finishCommit(batchId, actorPersonId);
        }
        return batchReader.get(batchId);
    }

    private void commitType(Long batchId, Long institutionId, ImportEntityType type) {
        List<Long> rowIds = progressRecorder.nextValidRowIds(batchId, type, CHUNK_SIZE);
        while (!rowIds.isEmpty()) {
            try {
                chunkTransaction.commit(batchId, institutionId, rowIds);
            } catch (RuntimeException e) {
                progressRecorder.markChunkFailed(rowIds, e.getClass().getSimpleName());
            }
            rowIds = progressRecorder.nextValidRowIds(batchId, type, CHUNK_SIZE);
        }
    }
}
