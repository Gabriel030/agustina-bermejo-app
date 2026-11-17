package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.entity.ImportCreatedEntity;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommitImportChunkTransaction {
    private final ImportRowRepository rowRepository;
    private final ImportCreatedEntityRepository createdEntityRepository;
    private final ImportDefinitionRegistry registry;
    private final EntityManager entityManager;

    public CommitImportChunkTransaction(ImportRowRepository rowRepository,
            ImportCreatedEntityRepository createdEntityRepository, ImportDefinitionRegistry registry, EntityManager entityManager) {
        this.rowRepository = rowRepository;
        this.createdEntityRepository = createdEntityRepository;
        this.registry = registry;
        this.entityManager = entityManager;
    }

    @Transactional
    public void commit(Long batchId, Long institutionId, List<Long> rowIds) {
        entityManager.setFlushMode(FlushModeType.COMMIT);
        List<ImportRow> rows = rowRepository.findAllById(rowIds).stream()
                .sorted(Comparator.comparingInt(ImportRow::getRowNumber)).toList();
        Map<ImportEntityType, BaseLookups> lookupsByType = new EnumMap<>(ImportEntityType.class);
        rows.stream().collect(Collectors.groupingBy(ImportRow::getEntityType)).forEach((type, typeRows) -> lookupsByType.put(type,
                registry.definitionFor(type).preloadForRecord(typeRows.stream().map(ImportRow::getPayload).toList())));
        for (ImportRow row : rows) {
            ImportEntityDefinition definition = registry.definitionFor(row.getEntityType());
            boolean parentMissing = definition.parentMissing(row.getPayload());
            Long entityId = definition.record(row.getPayload(), institutionId, lookupsByType.get(row.getEntityType()));
            if (parentMissing) {
                createdEntityRepository.save(new ImportCreatedEntity(batchId, row.getEntityType(), definition.parentIdOf(entityId),
                        definition.parentNaturalKey(row.getPayload()), ApplicationTime.now()));
            }
            row.markCommitted(entityId);
            createdEntityRepository.save(new ImportCreatedEntity(batchId, row.getEntityType(), entityId,
                    row.getNaturalKey(), ApplicationTime.now()));
        }
    }
}
