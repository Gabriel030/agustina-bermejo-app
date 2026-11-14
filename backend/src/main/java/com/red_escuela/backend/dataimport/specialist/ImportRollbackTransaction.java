package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchEvent;
import com.red_escuela.backend.dataimport.entity.ImportBatchEventType;
import com.red_escuela.backend.dataimport.entity.ImportCreatedEntity;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportRollbackBlockedException;
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.exception.HistoricalDiscardBlockedException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
public class ImportRollbackTransaction {
    public static final Set<ImportEntityType> RETAINED_TYPES = EnumSet.of(ImportEntityType.PROGRAMS,
            ImportEntityType.SUBJECTS, ImportEntityType.CURRICULUM, ImportEntityType.PREREQUISITES);
    private static final String LATER_DATA_REASON = "ya hay datos posteriores que dependen de lo importado";

    private final ImportBatchRepository batchRepository;
    private final ImportCreatedEntityRepository createdEntityRepository;
    private final ImportRowRepository rowRepository;
    private final ImportBatchEventRepository eventRepository;
    private final ImportDefinitionRegistry registry;

    public ImportRollbackTransaction(ImportBatchRepository batchRepository,
            ImportCreatedEntityRepository createdEntityRepository, ImportRowRepository rowRepository,
            ImportBatchEventRepository eventRepository, ImportDefinitionRegistry registry) {
        this.batchRepository = batchRepository;
        this.createdEntityRepository = createdEntityRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
    }

    @Transactional
    public Map<ImportEntityType, Long> execute(Long batchId, Long actorPersonId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        Map<ImportEntityType, Long> discarded = discardAll(batchId);
        createdEntityRepository.deleteByBatchIdAndEntityTypeNotIn(batchId, RETAINED_TYPES);
        rowRepository.moveStatusExcludingTypes(batchId, ImportRowStatus.COMMITTED, ImportRowStatus.VALID, RETAINED_TYPES);
        batch.finishRollback();
        Map<String, Object> detail = new LinkedHashMap<>();
        discarded.forEach((type, total) -> detail.put(type.name(), total));
        eventRepository.save(new ImportBatchEvent(batchId, ImportBatchEventType.ROLLED_BACK, actorPersonId,
                ApplicationTime.now(), detail));
        return discarded;
    }

    @Transactional
    public Map<ImportEntityType, Long> dryRun(Long batchId) {
        Map<ImportEntityType, Long> discarded = discardAll(batchId);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return discarded;
    }

    private Map<ImportEntityType, Long> discardAll(Long batchId) {
        Map<ImportEntityType, Long> discarded = new EnumMap<>(ImportEntityType.class);
        for (ImportCreatedEntity created : createdEntityRepository.findByBatchIdOrderByIdDesc(batchId)) {
            if (RETAINED_TYPES.contains(created.getEntityType())) {
                continue;
            }
            try {
                registry.definitionFor(created.getEntityType()).discard(created.getEntityId());
            } catch (HistoricalDiscardBlockedException e) {
                throw new ImportRollbackBlockedException(created.getEntityType(), created.getNaturalKey(), e.getMessage());
            } catch (DataIntegrityViolationException e) {
                throw new ImportRollbackBlockedException(created.getEntityType(), created.getNaturalKey(), LATER_DATA_REASON);
            }
            discarded.merge(created.getEntityType(), 1L, Long::sum);
        }
        return discarded;
    }
}
