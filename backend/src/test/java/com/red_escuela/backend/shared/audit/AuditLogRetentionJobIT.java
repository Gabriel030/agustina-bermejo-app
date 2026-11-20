package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.audit.service.AuditLogRetentionJob;
import com.red_escuela.backend.shared.retention.RetentionBatchPurger;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogRetentionJobIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 15, 3, 50);
    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private RetentionBatchPurger purger;

    @BeforeEach
    void buildPurger() {
        auditLogRepository.deleteAll();
        jdbc.update("delete from audit_log_archive");
        purger = new RetentionBatchPurger(namedJdbc, transactionManager, new SimpleMeterRegistry(),
                new RetentionSettings(true, 12, 12, 12, 36, 2, 100));
    }

    @AfterEach
    void cleanAuditLog() {
        auditLogRepository.deleteAll();
        jdbc.update("delete from audit_log_archive");
    }

    @Test
    void theScheduledJobIsOffByDefault() {
        long old = insert(NOW.minusMonths(60));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, false, 24).deleteExpiredAuditEntries();

        assertThat(deleted).isZero();
        assertThat(exists(old)).isTrue();
    }

    @Test
    void whenEnabledItDeletesOnlyEntriesOlderThanTheConfiguredMonths() {
        long expired = insert(NOW.minusMonths(25));
        long expiredToo = insert(NOW.minusMonths(30));
        long boundary = insert(NOW.minusMonths(24).plusMinutes(1));
        long recent = insert(NOW.minusDays(1));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, true, 24).deleteExpiredAuditEntries();

        assertThat(deleted).isEqualTo(2);
        assertThat(exists(expired)).isFalse();
        assertThat(exists(expiredToo)).isFalse();
        assertThat(exists(boundary)).isTrue();
        assertThat(exists(recent)).isTrue();
    }

    @Test
    void whenEnabledItAlsoDeletesArchivedEntriesOlderThanTheConfiguredMonths() {
        long archivedExpired = insertArchived(NOW.minusMonths(25));
        long archivedDayPast = insertArchived(NOW.minusMonths(24).minusDays(1));
        long archivedExactCutoff = insertArchived(NOW.minusMonths(24));
        long archivedRecent = insertArchived(NOW.minusMonths(13));
        long live = insert(NOW.minusDays(1));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, true, 24).deleteExpiredAuditEntries();

        assertThat(deleted).isEqualTo(2);
        assertThat(archived(archivedExpired)).isFalse();
        assertThat(archived(archivedDayPast)).isFalse();
        assertThat(archived(archivedExactCutoff)).isTrue();
        assertThat(archived(archivedRecent)).isTrue();
        assertThat(exists(live)).isTrue();
    }

    @Test
    void archivePurgeDeletesEveryExpiredRowAcrossSeveralBatches() {
        long[] expired = new long[5];
        for (int i = 0; i < expired.length; i++) {
            expired[i] = insertArchived(NOW.minusMonths(25).minusDays(i));
        }
        long keptAtCutoff = insertArchived(NOW.minusMonths(24));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, true, 24).deleteExpiredAuditEntries();

        assertThat(deleted).isEqualTo(5);
        for (long id : expired) {
            assertThat(archived(id)).isFalse();
        }
        assertThat(archived(keptAtCutoff)).isTrue();
    }

    @Test
    void theArchiveIsNotDeletedWhenRetentionIsOff() {
        long archivedExpired = insertArchived(NOW.minusMonths(60));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, false, 24).deleteExpiredAuditEntries();

        assertThat(deleted).isZero();
        assertThat(archived(archivedExpired)).isTrue();
    }

    @Test
    void aShorterConfiguredPeriodDeletesMore() {
        insert(NOW.minusMonths(7));
        long recent = insert(NOW.minusMonths(1));

        int deleted = new AuditLogRetentionJob(purger, CLOCK, true, 6).deleteExpiredAuditEntries();

        assertThat(deleted).isEqualTo(1);
        assertThat(exists(recent)).isTrue();
    }

    @Test
    void aRetentionPeriodBelowOneMonthIsRejectedAtStartup() {
        assertThatThrownBy(() -> new AuditLogRetentionJob(purger, CLOCK, true, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long insert(LocalDateTime occurredAt) {
        return jdbc.queryForObject("insert into audit_log (occurred_at, action, entity_type, entity_id) "
                + "values (?, 'STAFF_WITHDRAWN', 'PERSON', 1) returning id", Long.class, occurredAt);
    }

    private long insertArchived(LocalDateTime occurredAt) {
        long id = insert(occurredAt);
        jdbc.update("insert into audit_log_archive select * from audit_log where id = ?", id);
        jdbc.update("delete from audit_log where id = ?", id);
        return id;
    }

    private boolean archived(long id) {
        return jdbc.queryForObject("select count(*) from audit_log_archive where id = ?", Integer.class, id) == 1;
    }

    private boolean exists(long id) {
        return jdbc.queryForObject("select count(*) from audit_log where id = ?", Integer.class, id) == 1;
    }
}
