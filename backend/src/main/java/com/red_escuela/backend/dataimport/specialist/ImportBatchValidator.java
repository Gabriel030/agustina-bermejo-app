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
import com.red_escuela.backend.dataimport.repository.ImportRowCommittedKey;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportBatchValidator {
    private final ImportBatchRepository batchRepository;
    private final ImportFileRepository fileRepository;
    private final ImportRowRepository rowRepository;
    private final ImportBatchEventRepository eventRepository;
    private final ImportDefinitionRegistry registry;
    private final EntityManager entityManager;

    public ImportBatchValidator(ImportBatchRepository batchRepository, ImportFileRepository fileRepository,
            ImportRowRepository rowRepository, ImportBatchEventRepository eventRepository,
            ImportDefinitionRegistry registry, EntityManager entityManager) {
        this.batchRepository = batchRepository;
        this.fileRepository = fileRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
        this.entityManager = entityManager;
    }

    @Transactional
    public ImportBatch validate(Long batchId, Long actorPersonId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (!batch.canValidate()) {
            throw new ImportBatchStateInvalidException("validar", batch.getStatus().name());
        }
        entityManager.setFlushMode(FlushModeType.COMMIT);
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
        List<ImportRow> fileRows = rowRepository.findByBatchIdAndEntityTypeOrderByRowNumber(batchId, type);
        Map<Integer, List<ImportRowError>> acrossRows = definition.validateAcrossRows(fileRows.stream().map(ImportRow::getPayload).toList());
        ImportBatchKeys batchKeys = loadBatchKeys(batchId, definition, fileRows);
        Map<String, Boolean> baseExistence = new HashMap<>();
        int index = 0;
        for (List<ImportRow> chunk : InClauseChunks.of(fileRows)) {
            BaseLookups lookups = definition.preloadBase(chunk.stream().map(ImportRow::getPayload).toList());
            Set<ImportRowCommittedKey> committed = new HashSet<>(rowRepository.findCommittedKeys(type,
                    chunk.stream().map(ImportRow::getNaturalKey).collect(Collectors.toSet())));
            for (ImportRow row : chunk) {
                Map<String, String> payload = row.getPayload();
                List<ImportRowError> errors = new ArrayList<>(definition.validateFormat(payload));
                definition.uniquenessValues(payload).forEach((column, value) -> {
                    if (!seen.computeIfAbsent(column, key -> new HashSet<>()).add(value)) {
                        errors.add(new ImportRowError(column, column.toUpperCase(Locale.ROOT) + "_DUPLICATED_IN_FILE",
                                "El valor está repetido en el archivo"));
                    }
                });
                definition.references(payload).forEach(reference -> {
                    if (reference.prefix() && batchKeys.countStartingWith(reference.type(), reference.naturalKey(), 2) > 1) {
                        errors.add(new ImportRowError(reference.column(), reference.code().replace("_NOT_FOUND", "") + "_AMBIGUOUS",
                                "Hay más de una coincidencia en el lote: indicá la carrera"));
                    } else if (!referenceExists(batchKeys, reference, lookups, baseExistence)) {
                        errors.add(new ImportRowError(reference.column(), reference.code(), "La referencia no existe en el lote ni en el sistema"));
                    }
                });
                errors.addAll(acrossRows.getOrDefault(index, List.of()));
                ImportRowStatus status = classify(row, definition, errors, committed, lookups);
                row.classify(status, errors);
                counts.merge(status, 1, Integer::sum);
                index++;
            }
        }
        return counts;
    }

    private ImportBatchKeys loadBatchKeys(Long batchId, ImportEntityDefinition definition, List<ImportRow> fileRows) {
        Set<ImportEntityType> keyTypes = new HashSet<>();
        Set<ImportEntityType> aliasTypes = new HashSet<>();
        for (ImportRow row : fileRows) {
            for (ImportReference reference : definition.references(row.getPayload())) {
                (reference.alias() ? aliasTypes : keyTypes).add(reference.type());
            }
        }
        Map<ImportEntityType, NavigableSet<String>> naturalKeys = new HashMap<>();
        keyTypes.forEach(type -> naturalKeys.put(type, new TreeSet<>(rowRepository.findNaturalKeysByBatchIdAndEntityType(batchId, type))));
        Map<ImportEntityType, Set<String>> aliasKeys = new HashMap<>();
        aliasTypes.forEach(type -> {
            ImportEntityDefinition target = registry.definitionFor(type);
            aliasKeys.put(type, rowRepository.findByBatchIdAndEntityTypeOrderByRowNumber(batchId, type).stream()
                    .flatMap(row -> target.aliasKeys(row.getPayload()).stream()).collect(Collectors.toSet()));
        });
        return new ImportBatchKeys(naturalKeys, aliasKeys);
    }

    private boolean referenceExists(ImportBatchKeys batchKeys, ImportReference reference, BaseLookups lookups,
            Map<String, Boolean> baseExistence) {
        ImportEntityDefinition target = registry.definitionFor(reference.type());
        if (reference.alias()) {
            return batchKeys.containsAlias(reference.type(), reference.naturalKey())
                    || existsInBase(baseExistence, "alias", reference, () -> target.aliasExistsInBase(reference.naturalKey(), lookups));
        }
        if (reference.prefix() && batchKeys.countStartingWith(reference.type(), reference.naturalKey(), 1) > 0) {
            return true;
        }
        return !reference.prefix() && batchKeys.containsNaturalKey(reference.type(), reference.naturalKey())
                || existsInBase(baseExistence, "key", reference, () -> target.existsInBase(reference.naturalKey(), lookups));
    }

    private static boolean existsInBase(Map<String, Boolean> baseExistence, String kind, ImportReference reference,
            Supplier<Boolean> lookup) {
        return baseExistence.computeIfAbsent(kind + '\u0000' + reference.type() + '\u0000' + reference.naturalKey(), key -> lookup.get());
    }

    private ImportRowStatus classify(ImportRow row, ImportEntityDefinition definition, List<ImportRowError> errors,
            Set<ImportRowCommittedKey> committed, BaseLookups lookups) {
        if (!errors.isEmpty()) {
            return ImportRowStatus.ERROR;
        }
        if (committed.contains(new ImportRowCommittedKey(row.getNaturalKey(), row.getContentHash()))) {
            return ImportRowStatus.UNCHANGED;
        }
        ImportEntityDefinition.BaseCheck base = definition.checkAgainstBase(row.getPayload(), lookups);
        errors.addAll(base.errors());
        if (base.keyExists()) {
            return ImportRowStatus.CONFLICT;
        }
        return errors.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.ERROR;
    }
}
