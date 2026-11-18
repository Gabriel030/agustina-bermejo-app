package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.shared.audit.service.AuditLogArchiveJob;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogArchiveJobIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 15, 3, 35);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanInsertedRows() {
        jdbc.update("delete from audit_log where reason = 'archive-test'");
        jdbc.update("delete from audit_log_archive where reason = 'archive-test'");
        jdbc.update("delete from person where username like 'archiveactor%'");
    }

    private AuditLogArchiveJob job() {
        return job(100, 100);
    }

    private AuditLogArchiveJob job(int batchSize, int maxBatches) {
        return new AuditLogArchiveJob(auditLogRepository, new RetentionSettings(true, 12, 12, 12, 36, batchSize, maxBatches), CLOCK, transactionManager, 12);
    }

    private long countArchivedTest() {
        return jdbc.queryForObject("select count(*) from audit_log_archive where reason = 'archive-test'", Long.class);
    }

    private long insert(LocalDateTime occurredAt) {
        return jdbc.queryForObject("insert into audit_log (occurred_at, action, entity_type, entity_id, reason) "
                + "values (?, 'BROADCAST_CANCELLED', 'BROADCAST', 1, 'archive-test') returning id", Long.class, occurredAt);
    }

    private long total() {
        return jdbc.queryForObject("select (select count(*) from audit_log) + (select count(*) from audit_log_archive)", Long.class);
    }

    private boolean inAuditLog(long id) {
        return jdbc.queryForObject("select count(*) from audit_log where id = ?", Long.class, id) == 1;
    }

    private boolean inArchive(long id) {
        return jdbc.queryForObject("select count(*) from audit_log_archive where id = ?", Long.class, id) == 1;
    }

    @Test
    void auditLogArchiveKeepsEveryRow() {
        long oldest = insert(NOW.minusMonths(30));
        long old = insert(NOW.minusMonths(13));
        long older = insert(NOW.minusMonths(18));
        long boundary = insert(NOW.minusMonths(12).plusMinutes(1));
        long recent = insert(NOW.minusDays(1));
        long before = total();

        int moved = job().archiveExpiredAuditEntries();

        assertThat(moved).isGreaterThanOrEqualTo(3);
        assertThat(total()).isEqualTo(before);
        for (long id : new long[]{oldest, old, older}) {
            assertThat(inAuditLog(id)).isFalse();
            assertThat(inArchive(id)).isTrue();
        }
        assertThat(inAuditLog(boundary)).isTrue();
        assertThat(inAuditLog(recent)).isTrue();
        assertThat(jdbc.queryForObject("select reason from audit_log_archive where id = ?", String.class, oldest)).isEqualTo("archive-test");
        assertThat(job().archiveExpiredAuditEntries()).isZero();
        assertThat(total()).isEqualTo(before);
    }

    @Test
    void nothingBeforeTwelveMonths() {
        long boundary = insert(NOW.minusMonths(12).plusMinutes(1));
        long recent = insert(NOW.minusMonths(6));
        long before = total();

        job().archiveExpiredAuditEntries();

        assertThat(inAuditLog(boundary)).isTrue();
        assertThat(inAuditLog(recent)).isTrue();
        assertThat(inArchive(boundary)).isFalse();
        assertThat(total()).isEqualTo(before);
    }

    @Test
    void justPastTwelveMonthsIsArchivedAndExactCutoffIsNot() {
        long justPast = insert(NOW.minusMonths(12).minusMinutes(1));
        long dayPast = insert(NOW.minusMonths(12).minusDays(1));
        long exactCutoff = insert(NOW.minusMonths(12));
        long before = total();

        job().archiveExpiredAuditEntries();

        assertThat(inArchive(justPast)).isTrue();
        assertThat(inArchive(dayPast)).isTrue();
        assertThat(inAuditLog(exactCutoff)).isTrue();
        assertThat(inArchive(exactCutoff)).isFalse();
        assertThat(total()).isEqualTo(before);
    }

    @Test
    void archivesAcrossSeveralBatchesAndStopsAtMaxBatches() {
        for (int index = 0; index < 5; index++) {
            insert(NOW.minusMonths(13).minusDays(index));
        }
        long before = total();

        int firstRun = job(2, 2).archiveExpiredAuditEntries();

        assertThat(firstRun).isEqualTo(4);
        assertThat(countArchivedTest()).isEqualTo(4);

        int secondRun = job(2, 2).archiveExpiredAuditEntries();

        assertThat(secondRun).isEqualTo(1);
        assertThat(countArchivedTest()).isEqualTo(5);
        assertThat(total()).isEqualTo(before);
    }

    @Test
    void archivedRowKeepsEveryColumn() {
        Person actor = transactionTemplate.execute(status -> AudienceFreezeFixtures.person(entityManager, roleRepository,
                "archiveactor" + System.nanoTime(), true, "DIRECTOR"));
        Long id = jdbc.queryForObject("insert into audit_log (occurred_at, actor_person_id, actor_role, action, entity_type, entity_id, "
                + "before_value, after_value, reason) values (?, ?, 'DIRECTOR', 'STAFF_WITHDRAWN', 'PERSON', 77, 'ACTIVE', 'WITHDRAWN', 'archive-test') "
                + "returning id", Long.class, NOW.minusMonths(13), actor.getId());

        job().archiveExpiredAuditEntries();

        assertThat(inAuditLog(id)).isFalse();
        var archived = jdbc.queryForMap("select * from audit_log_archive where id = ?", id);
        assertThat(archived).containsEntry("actor_person_id", actor.getId())
                .containsEntry("actor_role", "DIRECTOR")
                .containsEntry("action", "STAFF_WITHDRAWN")
                .containsEntry("entity_type", "PERSON")
                .containsEntry("entity_id", 77L)
                .containsEntry("before_value", "ACTIVE")
                .containsEntry("after_value", "WITHDRAWN")
                .containsEntry("reason", "archive-test");
        assertThat(((java.sql.Timestamp) archived.get("occurred_at")).toLocalDateTime()).isEqualTo(NOW.minusMonths(13));
    }
}
