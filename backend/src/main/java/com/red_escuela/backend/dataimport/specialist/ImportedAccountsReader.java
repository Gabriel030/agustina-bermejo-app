package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.dataimport.api.ImportedAccountsPage;
import com.red_escuela.backend.dataimport.api.ImportedAccountsQuery;
import com.red_escuela.backend.dataimport.repository.ImportCreatedEntityRepository;
import com.red_escuela.backend.dataimport.repository.ImportedPersonRef;
import com.red_escuela.backend.identity.api.PendingActivationPerson;
import com.red_escuela.backend.identity.api.PendingActivationPersonFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportedAccountsReader implements ImportedAccountsQuery {
    private static final int MAX_LIMIT = 500;
    private static final int SCAN_CHUNK_SIZE = 500;

    private final ImportCreatedEntityRepository createdEntityRepository;
    private final PendingActivationPersonFinder pendingActivationPersonFinder;

    public ImportedAccountsReader(ImportCreatedEntityRepository createdEntityRepository,
            PendingActivationPersonFinder pendingActivationPersonFinder) {
        this.createdEntityRepository = createdEntityRepository;
        this.pendingActivationPersonFinder = pendingActivationPersonFinder;
    }

    @Override
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

    private List<ImportedPersonRef> findChunk(Long importBatchId, long afterPersonId) {
        PageRequest chunkPage = PageRequest.of(0, SCAN_CHUNK_SIZE);
        if (importBatchId == null) {
            return createdEntityRepository.findCreatedPersonsAfter(afterPersonId, chunkPage);
        }
        return createdEntityRepository.findCreatedPersonsOfBatchAfter(importBatchId, afterPersonId, chunkPage);
    }
}
