package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.entity.ImportCreatedEntity;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommitImportChunkTransaction {
    private final ImportRowRepository rowRepository;
    private final ImportCreatedEntityRepository createdEntityRepository;
    private final ImportDefinitionRegistry registry;

    public CommitImportChunkTransaction(ImportRowRepository rowRepository,
            ImportCreatedEntityRepository createdEntityRepository, ImportDefinitionRegistry registry) {
        this.rowRepository = rowRepository;
        this.createdEntityRepository = createdEntityRepository;
        this.registry = registry;
    }

    @Transactional
    public void commit(Long batchId, Long institutionId, List<Long> rowIds) {
        List<ImportRow> rows = rowRepository.findAllById(rowIds).stream()
                .sorted(Comparator.comparingInt(ImportRow::getRowNumber)).toList();
        for (ImportRow row : rows) {
            Long entityId = registry.definitionFor(row.getEntityType()).record(row.getPayload(), institutionId);
            row.markCommitted(entityId);
            createdEntityRepository.save(new ImportCreatedEntity(batchId, row.getEntityType(), entityId,
                    row.getNaturalKey(), ApplicationTime.now()));
        }
    }
}
