package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImportBatchCommitter {
    private static final int CHUNK_SIZE = 200;

    private final ImportBatchProgressRecorder progressRecorder;
    private final CommitImportChunkTransaction chunkTransaction;
    private final ImportDefinitionRegistry registry;

    public ImportBatchCommitter(ImportBatchProgressRecorder progressRecorder,
            CommitImportChunkTransaction chunkTransaction, ImportDefinitionRegistry registry) {
        this.progressRecorder = progressRecorder;
        this.chunkTransaction = chunkTransaction;
        this.registry = registry;
    }

    public void commitAll(Long batchId, Long institutionId) {
        for (ImportEntityType type : registry.commitOrder()) {
            commitType(batchId, institutionId, type);
        }
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
