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

    @Test
    void excludesInactiveAndDeletedStudents() {
        long batchId = insertBatch("COMMITTED");
        long active = importedPersonWithStatus(batchId, StatusType.ACTIVE);
        importedPersonWithStatus(batchId, StatusType.INACTIVE);
        importedPersonWithStatus(batchId, StatusType.DELETED);

        assertThat(allPendingIds(batchId)).containsExactly(active);
    }

    @Test
    void excludesAccountsOfBatchesBeingRolledBack() {
        long rollingBack = insertBatch("ROLLING_BACK");
        long committed = insertBatch("COMMITTED");
        long hidden = importedPerson(rollingBack, "ALUMNO", true, null, false);
        long visible = importedPerson(committed, "ALUMNO", true, null, false);

        assertThat(allPendingIdsWithoutBatchFilter()).contains(visible).doesNotContain(hidden);
        assertThat(allPendingIds(rollingBack)).isEmpty();
    }

    @Test
    void keepsAccountsOfPartialAndFailedBatches() {
        long partial = insertBatch("PARTIAL");
        long failed = insertBatch("FAILED");
        long partialPerson = importedPerson(partial, "ALUMNO", true, null, false);
        long failedPerson = importedPerson(failed, "ALUMNO", true, null, false);

        assertThat(allPendingIds(partial)).containsExactly(partialPerson);
        assertThat(allPendingIds(failed)).containsExactly(failedPerson);
    }

    @Test
    void crossesTheScanChunkBoundaryWithoutLosingOrRepeatingAccounts() {
        long batchId = insertBatch("COMMITTED");
        List<Long> created = importedPersons(batchId, 501, null);

        assertThat(collectWithLimit(batchId, 100)).containsExactlyElementsOf(created);
        ImportedAccountsPage first = importedAccountsQuery.findPendingActivation(batchId, null, 500);
        ImportedAccountsPage second = importedAccountsQuery.findPendingActivation(batchId, first.nextAfterPersonId(), 500);
        assertThat(first.accounts()).hasSize(500);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.accounts()).hasSize(1);
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void exactlyOneChunkOfAccountsEndsWithAnEmptyFinalPage() {
        long batchId = insertBatch("COMMITTED");
        importedPersons(batchId, 500, null);

        ImportedAccountsPage first = importedAccountsQuery.findPendingActivation(batchId, null, 500);
        ImportedAccountsPage second = importedAccountsQuery.findPendingActivation(batchId, first.nextAfterPersonId(), 500);

        assertThat(first.accounts()).hasSize(500);
        assertThat(second.accounts()).isEmpty();
        assertThat(second.hasMore()).isFalse();
    }

    @Test
    void aFullyActivatedChunkDoesNotHideThePendingAccountsAfterIt() {
        long batchId = insertBatch("COMMITTED");
        importedPersons(batchId, 500, "hash");
        List<Long> pending = importedPersons(batchId, 2, null);

        ImportedAccountsPage page = importedAccountsQuery.findPendingActivation(batchId, null, 10);

        assertThat(page.accounts()).extracting(ImportedAccount::personId).containsExactlyElementsOf(pending);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void aPersonInARolledBackAndACommittedBatchIsReportedForTheCommittedOne() {
        long reverted = insertBatch("ROLLED_BACK");
        long committed = insertBatch("COMMITTED");
        long personId = person("ALUMNO", true, null, false);
        linkToBatch(reverted, personId);
        linkToBatch(committed, personId);

        List<ImportedAccount> accounts = importedAccountsQuery.findPendingActivation(null, personId - 1, 1).accounts();

        assertThat(accounts).extracting(ImportedAccount::personId).containsExactly(personId);
        assertThat(accounts.get(0).importBatchId()).isEqualTo(committed);
    }

    @Test
    void aPersonInTwoValidBatchesIsReportedOnceWithTheLowestBatch() {
        long firstBatch = insertBatch("COMMITTED");
        long secondBatch = insertBatch("SEALED");
        long personId = person("ALUMNO", true, null, false);
        linkToBatch(secondBatch, personId);
        linkToBatch(firstBatch, personId);

        List<ImportedAccount> accounts = importedAccountsQuery.findPendingActivation(null, personId - 1, 1).accounts();

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).importBatchId()).isEqualTo(Math.min(firstBatch, secondBatch));
    }

    @Test
    void clampsTheLimitBetweenOneAndFiveHundred() {
        long batchId = insertBatch("COMMITTED");
        importedPersons(batchId, 501, null);

        assertThat(importedAccountsQuery.findPendingActivation(batchId, null, 0).accounts()).hasSize(1);
        assertThat(importedAccountsQuery.findPendingActivation(batchId, null, -7).accounts()).hasSize(1);
        assertThat(importedAccountsQuery.findPendingActivation(batchId, null, 5000).accounts()).hasSize(500);
    }

    @Test
    void startsStrictlyAfterTheGivenPersonId() {
        long batchId = insertBatch("COMMITTED");
        List<Long> created = importedPersons(batchId, 4, null);

        List<ImportedAccount> accounts = importedAccountsQuery.findPendingActivation(batchId, created.get(1), 10).accounts();

        assertThat(accounts).extracting(ImportedAccount::personId).containsExactly(created.get(2), created.get(3));
    }

    private List<Long> collectWithLimit(long batchId, int limit) {
        List<Long> ids = new ArrayList<>();
        Long cursor = null;
        do {
            ImportedAccountsPage page = importedAccountsQuery.findPendingActivation(batchId, cursor, limit);
            page.accounts().forEach(a -> ids.add(a.personId()));
            cursor = page.nextAfterPersonId();
        } while (cursor != null);
        return ids;
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
        linkToBatch(batchId, personId);
        return personId;
    }

    private long importedPersonWithStatus(long batchId, StatusType statusType) {
        long personId = transactions.execute(status -> persistPerson("ALUMNO", true, null, false, statusType));
        linkToBatch(batchId, personId);
        return personId;
    }

    private void linkToBatch(long batchId, long personId) {
        jdbc.update("""
                insert into import_created_entity (batch_id, entity_type, entity_id, natural_key, created_at)
                values (?, 'PERSONS', ?, ?, now())
                """, batchId, personId, "dni-" + batchId + "-" + personId);
    }

    private List<Long> importedPersons(long batchId, int count, String password) {
        List<Long> ids = new ArrayList<>();
        transactions.executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                ids.add(persistPerson("ALUMNO", true, password, false, StatusType.ACTIVE));
            }
        });
        ids.forEach(id -> linkToBatch(batchId, id));
        return ids;
    }

    private long person(String role, boolean withEmail, String password, boolean systemAccess) {
        return transactions.execute(status -> persistPerson(role, withEmail, password, systemAccess, StatusType.ACTIVE));
    }

    private long persistPerson(String role, boolean withEmail, String password, boolean systemAccess, StatusType statusType) {
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
        person.setStatus(statusType);
        person.setPassword(password);
        person.setGrantSystemAccess(systemAccess);
        person.getRoles().add(roleRepository.findByName(role).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person.getId();
    }
}
