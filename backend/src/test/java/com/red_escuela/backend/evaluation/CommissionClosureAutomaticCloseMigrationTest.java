package com.red_escuela.backend.evaluation;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class CommissionClosureAutomaticCloseMigrationTest {

    private static final String CLOSED_ROW = "insert into commission_closure "
            + "(section_kind, section_id, status, report_number, book, folio, closed_by_id, closed_at, closed_automatically) "
            + "values ('PROGRAM', ?, 'CLOSED', ?, ?, ?, ?, now(), ?)";

    private JdbcTemplate migrated(MigrationTestDatabase dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table commission_closure disable trigger all");
        jdbc.execute("alter table commission_closure_audit disable trigger all");
        return jdbc;
    }

    @Test
    void anAutomaticClosureNeedsNoBookFolioOrCloser() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("508")) {
            JdbcTemplate jdbc = migrated(dataSource);

            jdbc.update(CLOSED_ROW, 1, "ACTA-2026-0001", null, null, null, true);

            assertThat(jdbc.queryForObject("select closed_automatically from commission_closure", Boolean.class)).isTrue();
        }
    }

    @Test
    void aManualClosureStillNeedsBookFolioAndCloser() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("508")) {
            JdbcTemplate jdbc = migrated(dataSource);

            jdbc.update(CLOSED_ROW, 1, "ACTA-2026-0001", "L1", "F9", 7, false);

            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 2, "ACTA-2026-0002", null, "F9", 7, false))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 3, "ACTA-2026-0003", "L1", null, 7, false))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 4, "ACTA-2026-0004", "L1", "F9", null, false))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
        }
    }

    @Test
    void anAutomaticClosureStillNeedsReportNumberAndCannotHaveACloser() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("508")) {
            JdbcTemplate jdbc = migrated(dataSource);

            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 1, null, null, null, null, true))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 2, "ACTA-2026-0002", null, null, 7, true))
                    .hasMessageContaining("ck_commission_closure_automatic_has_no_actor");
        }
    }

    @Test
    void onlyTheAutomaticClosureAuditMayOmitTheActor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("508")) {
            JdbcTemplate jdbc = migrated(dataSource);
            jdbc.update("insert into commission_closure (section_kind, section_id, status) values ('PROGRAM', 1, 'DRAFT_AVAILABLE')");
            long closureId = jdbc.queryForObject("select id from commission_closure", Long.class);
            String insert = "insert into commission_closure_audit (closure_id, action, report_version, actor_person_id, occurred_at) "
                    + "values (?, ?, 1, ?, now())";

            jdbc.update(insert, closureId, "CLOSED_AUTOMATICALLY", null);
            jdbc.update(insert, closureId, "CLOSED", 7);

            assertThatThrownBy(() -> jdbc.update(insert, closureId, "CLOSED", null))
                    .hasMessageContaining("ck_commission_closure_audit_actor_required");
            assertThatThrownBy(() -> jdbc.update(insert, closureId, "REOPENED", null))
                    .hasMessageContaining("ck_commission_closure_audit_actor_required");
        }
    }
}
