package com.red_escuela.backend.dataimport.usecase;

import com.red_escuela.backend.dataimport.dto.ImportBatchResponse;
import com.red_escuela.backend.dataimport.specialist.ImportActorResolver;
import com.red_escuela.backend.dataimport.specialist.ImportBatchCommitter;
import com.red_escuela.backend.dataimport.specialist.ImportBatchProgressRecorder;
import com.red_escuela.backend.dataimport.specialist.ImportBatchReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RetryImportBatchService implements RetryImportBatchUseCase {
    private final ImportBatchProgressRecorder progressRecorder;
    private final ImportBatchCommitter committer;
    private final ImportBatchReader batchReader;
    private final ImportActorResolver actorResolver;

    public RetryImportBatchService(ImportBatchProgressRecorder progressRecorder, ImportBatchCommitter committer,
            ImportBatchReader batchReader, ImportActorResolver actorResolver) {
        this.progressRecorder = progressRecorder;
        this.committer = committer;
        this.batchReader = batchReader;
        this.actorResolver = actorResolver;
    }

    @Override
    public ImportBatchResponse retry(Long batchId) {
        Long actorPersonId = actorResolver.currentPersonId();
        Long institutionId = progressRecorder.startRetry(batchId);
        try {
            committer.commitAll(batchId, institutionId);
        } finally {
            progressRecorder.finishRetry(batchId, actorPersonId);
        }
        return batchReader.get(batchId);
    }
}
