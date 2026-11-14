package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchEvent;
import com.red_escuela.backend.dataimport.entity.ImportBatchEventType;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportFile;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportBatchStateInvalidException;
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportFileRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportBatchValidator {
    private final ImportBatchRepository batchRepository;
    private final ImportFileRepository fileRepository;
    private final ImportRowRepository rowRepository;
    private final ImportBatchEventRepository eventRepository;
    private final ImportDefinitionRegistry registry;

    public ImportBatchValidator(ImportBatchRepository batchRepository, ImportFileRepository fileRepository,
            ImportRowRepository rowRepository, ImportBatchEventRepository eventRepository,
            ImportDefinitionRegistry registry) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
    }

    @Transactional
    public ImportBatch validate(Long batchId, Long actorPersonId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (!batch.canValidate()) {
            throw new ImportBatchStateInvalidException("validar", batch.getStatus().name());
        }
        boolean hasIssues = false;
        Map<String, Object> detail = new HashMap<>();
        for (ImportFile file : fileRepository.findByBatchIdOrderByEntityType(batchId)) {
            Map<ImportRowStatus, Integer> counts = validateFile(batchId, file.getEntityType());
            hasIssues |= counts.getOrDefault(ImportRowStatus.ERROR, 0) + counts.getOrDefault(ImportRowStatus.CONFLICT, 0) > 0;
            detail.put(file.getEntityType().name(), counts.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)));
        }
        LocalDateTime now = ApplicationTime.now();
        batch.markValidated(hasIssues, now);
        eventRepository.save(new ImportBatchEvent(batchId, ImportBatchEventType.VALIDATED, actorPersonId, now, detail));
        return batch;
    }

    private Map<ImportRowStatus, Integer> validateFile(Long batchId, ImportEntityType type) {
        ImportEntityDefinition definition = registry.definitionFor(type);
        Map<String, Set<String>> seen = new HashMap<>();
        Map<ImportRowStatus, Integer> counts = new HashMap<>();
        for (ImportRow row : rowRepository.findByBatchIdAndEntityTypeOrderByRowNumber(batchId, type)) {
            Map<String, String> payload = row.getPayload();
            List<ImportRowError> errors = new ArrayList<>(definition.validateFormat(payload));
            definition.uniquenessValues(payload).forEach((column, value) -> {
                if (!seen.computeIfAbsent(column, key -> new HashSet<>()).add(value)) {
                    errors.add(new ImportRowError(column, column.toUpperCase(Locale.ROOT) + "_DUPLICATED_IN_FILE",
                            "El valor está repetido en el archivo"));
                }
            });
            ImportRowStatus status = classify(row, definition, errors);
            row.classify(status, errors);
            counts.merge(status, 1, Integer::sum);
        }
        return counts;
    }

    private ImportRowStatus classify(ImportRow row, ImportEntityDefinition definition, List<ImportRowError> errors) {
        if (!errors.isEmpty()) {
            return ImportRowStatus.ERROR;
        }
        if (rowRepository.existsByEntityTypeAndNaturalKeyAndContentHashAndStatus(row.getEntityType(),
                row.getNaturalKey(), row.getContentHash(), ImportRowStatus.COMMITTED)) {
            return ImportRowStatus.UNCHANGED;
        }
        ImportEntityDefinition.BaseCheck base = definition.checkAgainstBase(row.getPayload());
        errors.addAll(base.errors());
        if (base.keyExists()) {
            return ImportRowStatus.CONFLICT;
        }
        return errors.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.ERROR;
    }
}
