package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ImportRowCommittedLookupIndexIT extends AbstractIntegrationTest {
    private static final String COMMITTED_LOOKUP_INDEX = "ix_import_row_committed_natural_key";
    private static final int ROWS_PER_BATCH = 2_000;

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void committedRowLookupByNaturalKeyAndContentHashUsesTheDedicatedIndex() {
        transactions.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long institutionId = persistInstitution();
            long personId = persistPerson();
            long firstBatch = insertBatch(institutionId, personId);
            long secondBatch = insertBatch(institutionId, personId);
            insertRows(firstBatch, "COMMITTED");
            insertRows(secondBatch, "COMMITTED");
            insertRows(secondBatch, "VALID");
            jdbc.execute("analyze import_row");
            jdbc.execute("set local enable_seqscan = off");

            List<String> plan = jdbc.queryForList("""
                    explain select id from import_row
                    where entity_type = 'SECTION_ENROLLMENTS' and natural_key = 'key-1500'
                      and content_hash = 'hash-1500' and status = 'COMMITTED' limit 1
                    """, String.class);

            assertThat(String.join("\n", plan)).contains(COMMITTED_LOOKUP_INDEX);
        });
    }

    private void insertRows(long batchId, String rowStatus) {
        jdbc.update("""
                insert into import_row (batch_id, entity_type, row_number, natural_key, content_hash, payload, status)
                select ?, 'SECTION_ENROLLMENTS', n, 'key-' || n, 'hash-' || n, '{}'::jsonb, ?
                from generate_series(1, ?) as n
                """, batchId, rowStatus, ROWS_PER_BATCH);
    }

    private long insertBatch(long institutionId, long personId) {
        return jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at)
                values ('Indice', 'COMMITTED', ?, ?, now()) returning id
                """, Long.class, institutionId, personId);
    }

    private long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private long persistPerson() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String unique = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(unique);
        person.setDni(unique.substring(0, 12));
        person.setUsername("lookup." + unique);
        person.setEmail("lookup." + unique + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person.getId();
    }
}
