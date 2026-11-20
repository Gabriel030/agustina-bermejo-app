package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchCommitter;
import com.red_escuela.backend.dataimport.specialist.ImportBatchProgressRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CommitImportBatchService implements CommitImportBatchUseCase {
    private final ImportBatchProgressRecorder progressRecorder;
    private final ImportBatchCommitter committer;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver currentActorFinder;

    public CommitImportBatchService(ImportBatchProgressRecorder progressRecorder, ImportBatchCommitter committer, ImportBatchReader batchReader, ImportActorResolver currentActorFinder) {
        this.progressRecorder = progressRecorder;
        this.committer = committer;
        this.batchReader = batchReader;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public ImportBatchResponse commit(Long batchId, boolean onlyValidRows) {
        Long actorPersonId = currentActorFinder.currentPersonId();
        Long institutionId = progressRecorder.startCommit(batchId, onlyValidRows);
        try {
            committer.commitAll(batchId, institutionId);
        } finally {
            progressRecorder.finishCommit(batchId, actorPersonId);
        }
        return batchReader.get(batchId);
    }
}
