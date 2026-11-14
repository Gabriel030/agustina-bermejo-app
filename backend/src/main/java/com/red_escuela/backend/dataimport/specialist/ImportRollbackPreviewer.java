package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse;
import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse.Blocker;
import com.red_escuela.backend.dataimport.dto.ImportRollbackPreviewResponse.CreatedCount;
import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportBatchStateInvalidException;
import com.red_escuela.backend.dataimport.exception.ImportRollbackBlockedException;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportCreatedCount;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImportRollbackPreviewer {
    private final ImportBatchRepository batchRepository;
    private final ImportCreatedEntityRepository createdEntityRepository;
    private final ImportRollbackTransaction rollbackTransaction;

    public ImportRollbackPreviewer(ImportBatchRepository batchRepository,
            ImportCreatedEntityRepository createdEntityRepository, ImportRollbackTransaction rollbackTransaction) {
        this.batchRepository = batchRepository;
        this.createdEntityRepository = createdEntityRepository;
        this.rollbackTransaction = rollbackTransaction;
    }

    public ImportRollbackPreviewResponse preview(Long batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (!batch.canRollback()) {
            throw new ImportBatchStateInvalidException("deshacer", batch.getStatus().name());
        }
        List<ImportCreatedCount> counts = createdEntityRepository.countByEntityType(batchId).stream()
                .sorted(Comparator.comparing(ImportCreatedCount::entityType)).toList();
        List<CreatedCount> created = counts.stream()
                .filter(count -> !ImportRollbackTransaction.RETAINED_TYPES.contains(count.entityType()))
                .map(count -> new CreatedCount(count.entityType(), count.total())).toList();
        List<CreatedCount> retained = counts.stream()
                .filter(count -> ImportRollbackTransaction.RETAINED_TYPES.contains(count.entityType()))
                .map(count -> new CreatedCount(count.entityType(), count.total())).toList();
        try {
            rollbackTransaction.dryRun(batchId);
            return new ImportRollbackPreviewResponse(true, created, retained, List.of());
        } catch (ImportRollbackBlockedException e) {
            return new ImportRollbackPreviewResponse(false, created, retained,
                    List.of(new Blocker(e.entityType(), e.naturalKey(), e.reason())));
        }
    }
}
