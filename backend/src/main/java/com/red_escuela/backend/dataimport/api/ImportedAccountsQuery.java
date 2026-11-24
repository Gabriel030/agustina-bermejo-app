package com.red_escuela.backend.dataimport.api;

import com.red_escuela.backend.dataimport.entity.ImportBatchStatus;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import com.red_escuela.backend.dataimport.repository.ImportedPersonRef;
import com.red_escuela.backend.identity.api.PendingActivationPerson;
import com.red_escuela.backend.identity.api.PendingActivationPersonFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportedAccountsQuery {
    private static final int MAX_LIMIT = 500;
    private static final int SCAN_CHUNK_SIZE = 500;
    private static final List<ImportBatchStatus> REVERTED_STATUSES =
            List.of(ImportBatchStatus.ROLLED_BACK, ImportBatchStatus.ROLLING_BACK);

    private final ImportCreatedEntityRepository createdEntityRepository;
    private final ImportBatchRepository importBatchRepository;
    private final PendingActivationPersonFinder pendingActivationPersonFinder;

    public ImportedAccountsQuery(ImportCreatedEntityRepository createdEntityRepository,
            ImportBatchRepository importBatchRepository,
            PendingActivationPersonFinder pendingActivationPersonFinder) {
        this.createdEntityRepository = createdEntityRepository;
        this.importBatchRepository = importBatchRepository;
        this.pendingActivationPersonFinder = pendingActivationPersonFinder;
    }

    @Transactional(readOnly = true)
    public boolean importBatchExists(Long importBatchId) {
        return importBatchId != null && importBatchRepository.existsById(importBatchId);
    }

    @Transactional(readOnly = true)
    public Optional<String> findImportBatchName(Long importBatchId) {
        if (importBatchId == null) {
            return Optional.empty();
        }
        return importBatchRepository.findById(importBatchId).map(batch -> batch.getName());
    }

    @Transactional(readOnly = true)
    public ImportedAccountsPage findPendingActivation(Long importBatchId, Long afterPersonId, int limit) {
        int pageSize = Math.max(1, Math.min(limit, MAX_LIMIT));
        long cursor = afterPersonId == null ? 0L : afterPersonId;
        List<ImportedAccount> accounts = new ArrayList<>();
        while (true) {
            List<ImportedPersonRef> chunk = findChunk(importBatchId, cursor);
            if (chunk.isEmpty()) {
                return new ImportedAccountsPage(accounts, null);
            }
            Map<Long, PendingActivationPerson> pendingById = pendingActivationPersonFinder
                    .findStudentsPendingActivationByIdIn(chunk.stream().map(ImportedPersonRef::personId).toList())
                    .stream()
                    .collect(Collectors.toMap(PendingActivationPerson::personId, Function.identity()));
            for (ImportedPersonRef ref : chunk) {
                cursor = ref.personId();
                PendingActivationPerson pending = pendingById.get(ref.personId());
                if (pending != null) {
                    accounts.add(new ImportedAccount(pending.personId(), pending.email(), pending.displayName(), ref.batchId()));
                    if (accounts.size() == pageSize) {
                        return new ImportedAccountsPage(accounts, cursor);
                    }
                }
            }
            if (chunk.size() < SCAN_CHUNK_SIZE) {
                return new ImportedAccountsPage(accounts, null);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ImportedBatchSummary> findImportedBatchesWithPendingActivation() {
        return importBatchRepository.findByStatusNotInOrderByIdDesc(REVERTED_STATUSES).stream()
                .map(batch -> new ImportedBatchSummary(batch.getId(), batch.getName(), batch.getCommittedAt(),
                        countPendingActivation(batch.getId())))
                .filter(summary -> summary.studentsPendingActivation() > 0)
                .toList();
    }

    private long countPendingActivation(Long importBatchId) {
        long pending = 0;
        long cursor = 0L;
        while (true) {
            List<ImportedPersonRef> chunk = findChunk(importBatchId, cursor);
            if (chunk.isEmpty()) {
                return pending;
            }
            pending += pendingActivationPersonFinder
                    .findStudentsPendingActivationByIdIn(chunk.stream().map(ImportedPersonRef::personId).toList()).size();
            cursor = chunk.get(chunk.size() - 1).personId();
            if (chunk.size() < SCAN_CHUNK_SIZE) {
                return pending;
            }
        }
    }

    private List<ImportedPersonRef> findChunk(Long importBatchId, long afterPersonId) {
        PageRequest chunkPage = PageRequest.of(0, SCAN_CHUNK_SIZE);
        if (importBatchId == null) {
            return createdEntityRepository.findCreatedPersonsAfter(afterPersonId, chunkPage);
        }
        return createdEntityRepository.findCreatedPersonsOfBatchAfter(importBatchId, afterPersonId, chunkPage);
    }
}
