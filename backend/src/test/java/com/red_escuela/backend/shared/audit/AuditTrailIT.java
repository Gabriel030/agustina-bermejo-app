package com.red_escuela.backend.shared.audit;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import com.red_escuela.backend.shared.audit.service.AuditLogReader;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@SharedMockBeans
class AuditTrailIT extends AbstractIntegrationTest {

    private static final AuditRecord RECORD = new AuditRecord(AuditAction.STAFF_REACTIVATED, AuditEntityType.PERSON,
            77L, "INACTIVE", "ACTIVE", "motivo");

    @Autowired
    private AuditTrail auditTrail;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private AuditLogReader auditLogReader;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanAuditLog() {
        auditLogRepository.deleteAll();
    }

    @Test
    void recordInsideATransactionPersistsTheEntryWhenItCommits() {
        transactionTemplate.executeWithoutResult(status -> auditTrail.record(RECORD));

        List<AuditLogEntry> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getEntityId()).isEqualTo(77L);
        assertThat(entries.get(0).getAction()).isEqualTo(AuditAction.STAFF_REACTIVATED);
        assertThat(entries.get(0).getOccurredAt()).isNotNull();
    }

    @Test
    void staffCreatedActionIsAcceptedByTheDatabaseCheckConstraint() {
        transactionTemplate.executeWithoutResult(status -> auditTrail.record(
                new AuditRecord(AuditAction.STAFF_CREATED, AuditEntityType.PERSON, 78L, null, "DOCENTE", null)));

        assertThat(auditLogRepository.findAll()).extracting(AuditLogEntry::getAction)
                .containsExactly(AuditAction.STAFF_CREATED);
    }

    @Test
    void theEntryRollsBackTogetherWithTheActionThatFailsAfterRecordingIt() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            auditTrail.record(RECORD);
            throw new IllegalStateException("the action failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void recordOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> auditTrail.record(RECORD)).isInstanceOf(IllegalTransactionStateException.class);

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void theAuditTableHoldsOnlyTheAgreedNonSensitiveColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'audit_log'", String.class);

        assertThat(columns).containsExactlyInAnyOrderElementsOf(Set.of(
                "id", "occurred_at", "actor_person_id", "actor_role", "action", "entity_type", "entity_id",
                "before_value", "after_value", "reason"));
    }

    @Test
    void theDatabaseRejectsAnActionOutsideTheClosedList() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'UNKNOWN', 'PERSON', 1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void readerReturnsNewestFirstAndFiltersByAction() {
        transactionTemplate.executeWithoutResult(status -> {
            auditTrail.record(new AuditRecord(AuditAction.STAFF_WITHDRAWN, AuditEntityType.PERSON, 1L, "ACTIVE", "INACTIVE", null));
            auditTrail.record(RECORD);
        });

        var all = auditLogReader.search(AuditLogFilter.none(), 0, 10, ids -> java.util.Map.of());
        var withdrawn = auditLogReader.search(new AuditLogFilter(java.util.List.of(AuditAction.STAFF_WITHDRAWN), null, null, null, null, null, null), 0, 10, ids -> java.util.Map.of());
        var forEntity = auditLogReader.search(new AuditLogFilter(null, null, 77L, null, null, null, null), 0, 10, ids -> java.util.Map.of());

        assertThat(all.totalElements()).isEqualTo(2);
        assertThat(all.items().get(0).action()).isEqualTo(AuditAction.STAFF_REACTIVATED);
        assertThat(withdrawn.items()).extracting(AuditLogView::entityId).containsExactly(1L);
        assertThat(forEntity.items()).extracting(AuditLogView::entityId).containsExactly(77L);
    }
}
