package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.audit.service.AuditLogArchiveJob;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

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

    private AuditLogArchiveJob job() {
        return new AuditLogArchiveJob(auditLogRepository, new RetentionSettings(true, 12, 12, 12, 36, 2, 100), CLOCK, transactionManager, 12);
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
}
