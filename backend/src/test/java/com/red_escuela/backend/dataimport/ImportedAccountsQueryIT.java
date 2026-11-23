package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.dataimport.api.ImportedAccountsPage;
import com.red_escuela.backend.dataimport.api.ImportedAccountsQuery;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ImportedAccountsQueryIT extends AbstractDataImportIT {
    @Autowired
    private ImportedAccountsQuery importedAccountsQuery;
    @Autowired
    private RoleRepository roleRepository;

    @Test
    void returnsOnlyImportedStudentsThatHaveNotActivated() {
        long batchId = insertBatch("COMMITTED");
        long pending = importedPerson(batchId, "ALUMNO", true, null, false);
        long withPassword = importedPerson(batchId, "ALUMNO", true, "hash", false);
        long withAccess = importedPerson(batchId, "ALUMNO", true, null, true);
        long notStudent = importedPerson(batchId, "DOCENTE", true, null, false);
        long notImported = person("ALUMNO", true, null, false);

        List<Long> ids = allPendingIds(batchId);

        assertThat(ids).containsExactly(pending);
        assertThat(ids).doesNotContain(withPassword, withAccess, notStudent, notImported);
    }

    @Test
    void excludesAccountsOfRolledBackBatches() {
        long kept = insertBatch("COMMITTED");
        long reverted = insertBatch("ROLLED_BACK");
        long keptPerson = importedPerson(kept, "ALUMNO", true, null, false);
        long revertedPerson = importedPerson(reverted, "ALUMNO", true, null, false);

        List<Long> ids = allPendingIdsWithoutBatchFilter();

        assertThat(ids).contains(keptPerson).doesNotContain(revertedPerson);
    }

    @Test
    void filtersByImportBatch() {
        long first = insertBatch("COMMITTED");
        long second = insertBatch("SEALED");
        long firstPerson = importedPerson(first, "ALUMNO", true, null, false);
        long secondPerson = importedPerson(second, "ALUMNO", true, null, false);

        assertThat(allPendingIds(first)).containsExactly(firstPerson);
        assertThat(allPendingIds(second)).containsExactly(secondPerson);
        assertThat(allPendingIdsWithoutBatchFilter()).contains(firstPerson, secondPerson);
    }

    @Test
    void reportsAccountsWithoutEmailAndKeepsBatchAndName() {
        long batchId = insertBatch("COMMITTED");
        long withEmail = importedPerson(batchId, "ALUMNO", true, null, false);
        long withoutEmail = importedPerson(batchId, "ALUMNO", false, null, false);

        List<ImportedAccount> accounts = importedAccountsQuery.findPendingActivation(batchId, null, 10).accounts();

        ImportedAccount mailed = accounts.stream().filter(a -> a.personId() == withEmail).findFirst().orElseThrow();
        ImportedAccount mailless = accounts.stream().filter(a -> a.personId() == withoutEmail).findFirst().orElseThrow();
        assertThat(mailed.withoutEmail()).isFalse();
        assertThat(mailed.email()).isNotBlank();
        assertThat(mailed.importBatchId()).isEqualTo(batchId);
        assertThat(mailed.displayName()).isEqualTo("Importada " + suffix);
        assertThat(mailless.withoutEmail()).isTrue();
        assertThat(mailless.email()).isEmpty();
    }

    @Test
    void pagesByCursorWithoutRepeatingAccounts() {
        long batchId = insertBatch("COMMITTED");
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(importedPerson(batchId, "ALUMNO", true, null, false));
        }
        long activated = importedPerson(batchId, "ALUMNO", true, "hash", false);

        ImportedAccountsPage first = importedAccountsQuery.findPendingActivation(batchId, null, 2);
        ImportedAccountsPage second = importedAccountsQuery.findPendingActivation(batchId, first.nextAfterPersonId(), 2);
        ImportedAccountsPage third = importedAccountsQuery.findPendingActivation(batchId, second.nextAfterPersonId(), 2);

        assertThat(first.accounts()).hasSize(2);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.accounts()).hasSize(2);
        assertThat(third.accounts()).hasSize(1);
        assertThat(third.hasMore()).isFalse();
        List<Long> seen = new ArrayList<>();
        for (ImportedAccountsPage page : List.of(first, second, third)) {
            page.accounts().forEach(a -> seen.add(a.personId()));
        }
        assertThat(seen).containsExactlyElementsOf(created).doesNotContain(activated);
    }

    private List<Long> allPendingIds(long batchId) {
        return collect(batchId);
    }

    private List<Long> allPendingIdsWithoutBatchFilter() {
        return collect(null);
    }

    private List<Long> collect(Long batchId) {
        List<Long> ids = new ArrayList<>();
        Long cursor = null;
        do {
            ImportedAccountsPage page = importedAccountsQuery.findPendingActivation(batchId, cursor, 100);
            page.accounts().forEach(a -> ids.add(a.personId()));
            cursor = page.nextAfterPersonId();
        } while (cursor != null);
        return ids;
    }

    private long insertBatch(String status) {
        Long institutionId = jdbc.queryForObject("select id from institution limit 1", Long.class);
        return jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at, version)
                values ('Lote ' || ?, ?, ?, ?, now(), 0) returning id
                """, Long.class, suffix, status, institutionId, directorId);
    }

    private long importedPerson(long batchId, String role, boolean withEmail, String password, boolean systemAccess) {
        long personId = person(role, withEmail, password, systemAccess);
        jdbc.update("""
                insert into import_created_entity (batch_id, entity_type, entity_id, natural_key, created_at)
                values (?, 'PERSONS', ?, ?, now())
                """, batchId, personId, "dni-" + personId);
        return personId;
    }

    private long person(String role, boolean withEmail, String password, boolean systemAccess) {
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            String unique = UUID.randomUUID().toString();
            Person person = new Person();
            person.setName("Importada");
            person.setLastname(suffix);
            person.setDni(unique.substring(0, 12));
            person.setUsername("imported." + unique);
            person.setEmail(withEmail ? "imported." + unique + "@example.com" : null);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.setPassword(password);
            person.setGrantSystemAccess(systemAccess);
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return person.getId();
        });
    }
}
