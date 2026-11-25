package com.red_escuela.backend.dataimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportCreatedEntity;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ImportRowInsertBatchingIT extends AbstractIntegrationTest {
    private static final int ROWS = 200;
    private static final int MAX_STATEMENTS_FOR_ROWS = 12;

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void importRowInsertsAreSentInBatchesInsteadOfOneStatementPerRow() {
        transactions.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long batchId = persistBatch();

            QueryCountAssertions.assertAtMostQueries(entityManagerFactory, MAX_STATEMENTS_FOR_ROWS, () -> {
                for (int n = 1; n <= ROWS; n++) {
                    entityManager.persist(new ImportRow(batchId, ImportEntityType.PERSONS, n, "key-" + n, "hash-" + n,
                            Map.of("n", String.valueOf(n))));
                }
                entityManager.flush();
                return null;
            });

            assertThat(jdbc.queryForObject("select count(*) from import_row where batch_id = ?", Long.class, batchId))
                    .isEqualTo(ROWS);
        });
    }

    @Test
    void importCreatedEntityInsertsAreSentInBatchesInsteadOfOneStatementPerRow() {
        transactions.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long batchId = persistBatch();

            QueryCountAssertions.assertAtMostQueries(entityManagerFactory, MAX_STATEMENTS_FOR_ROWS, () -> {
                for (int n = 1; n <= ROWS; n++) {
                    entityManager.persist(new ImportCreatedEntity(batchId, ImportEntityType.PERSONS, (long) n, "key-" + n,
                            LocalDateTime.now()));
                }
                entityManager.flush();
                return null;
            });

            assertThat(jdbc.queryForObject("select count(*) from import_created_entity where batch_id = ?", Long.class,
                    batchId)).isEqualTo(ROWS);
        });
    }

    private long persistBatch() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String unique = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(unique);
        person.setDni(unique.substring(0, 12));
        person.setUsername("batching." + unique);
        person.setEmail("batching." + unique + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return jdbc.queryForObject("""
                insert into import_batch (name, status, institution_id, created_by_person_id, created_at)
                values ('Batching', 'DRAFT', ?, ?, now()) returning id
                """, Long.class, institution.getId(), person.getId());
    }
}
