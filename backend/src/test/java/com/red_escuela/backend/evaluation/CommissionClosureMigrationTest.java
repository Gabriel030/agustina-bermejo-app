package com.red_escuela.backend.evaluation;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class CommissionClosureMigrationTest {

    private static final String CLOSED_ROW = "insert into commission_closure "
            + "(section_kind, section_id, status, report_number, book, folio, closed_by_id, closed_at) "
            + "values ('PROGRAM', ?, 'CLOSED', ?, ?, ?, ?, now())";

    private JdbcTemplate migrated(MigrationTestDatabase dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table commission_closure disable trigger all");
        jdbc.execute("alter table commission_closure_student disable trigger all");
        jdbc.execute("alter table commission_closure_audit disable trigger all");
        return jdbc;
    }

    private void insertDraft(JdbcTemplate jdbc, String kind, long sectionId) {
        jdbc.update("insert into commission_closure (section_kind, section_id, status) values (?, ?, 'DRAFT_AVAILABLE')", kind, sectionId);
    }

    private long insertDraftAndReturnId(JdbcTemplate jdbc, long sectionId) {
        insertDraft(jdbc, "PROGRAM", sectionId);
        return jdbc.queryForObject("select id from commission_closure where section_id = ?", Long.class, sectionId);
    }

    @Test
    void aClosedClosureRequiresReportNumberBookFolioAndCloser() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);

            jdbc.update(CLOSED_ROW, 1, "ACTA-2026-0001", "L1", "F9", 7);

            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 2, null, "L1", "F9", 7))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 3, "ACTA-2026-0003", null, "F9", 7))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 4, "ACTA-2026-0004", "L1", null, 7))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 5, "ACTA-2026-0005", "L1", "F9", null))
                    .hasMessageContaining("ck_commission_closure_closed_has_report");
        }
    }

    @Test
    void aNonClosedClosureNeedsNoReportData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);

            insertDraft(jdbc, "PROGRAM", 1);
            jdbc.update("insert into commission_closure (section_kind, section_id, status) values ('COURSE', 1, 'REOPENED')");

            assertThat(jdbc.queryForObject("select count(*) from commission_closure", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void aSectionHasAtMostOneClosureAndReportNumbersAreUnique() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);
            insertDraft(jdbc, "PROGRAM", 1);
            jdbc.update(CLOSED_ROW, 2, "ACTA-2026-0001", "L1", "F9", 7);

            assertThatThrownBy(() -> insertDraft(jdbc, "PROGRAM", 1)).hasMessageContaining("uq_commission_closure_section");
            assertThatThrownBy(() -> jdbc.update(CLOSED_ROW, 3, "ACTA-2026-0001", "L1", "F9", 7))
                    .hasMessageContaining("uq_commission_closure_report_number");
            insertDraft(jdbc, "COURSE", 1);
        }
    }

    @Test
    void unknownKindStatusAndConditionAreRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);
            long closureId = insertDraftAndReturnId(jdbc, 1);

            assertThatThrownBy(() -> insertDraft(jdbc, "OTHER", 9)).hasMessageContaining("ck_commission_closure_section_kind");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure (section_kind, section_id, status) values ('PROGRAM', 9, 'OPEN')"))
                    .hasMessageContaining("ck_commission_closure_status");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure_student (closure_id, enrollment_id, final_condition) values (?, 1, 'PASSED')",
                    closureId)).hasMessageContaining("ck_commission_closure_student_condition");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure_audit (closure_id, action, report_version, actor_person_id, occurred_at) "
                            + "values (?, 'DELETED', 1, 1, now())", closureId))
                    .hasMessageContaining("ck_commission_closure_audit_action");
        }
    }

    @Test
    void aPromotingStudentNeedsAPromotionGradeWithDecimalsAndRequirementsMayBeUnverified() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);
            long closureId = insertDraftAndReturnId(jdbc, 1);

            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure_student (closure_id, enrollment_id, final_condition) values (?, 1, 'PROMOTES')",
                    closureId)).hasMessageContaining("ck_commission_closure_student_promotion_grade");
            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure_student (closure_id, enrollment_id, final_condition) values (?, 2, 'PROMOTION_CONDITIONAL')",
                    closureId)).hasMessageContaining("ck_commission_closure_student_promotion_grade");
            jdbc.update("insert into commission_closure_student (closure_id, enrollment_id, final_condition, promotion_grade, "
                    + "attendance_ok, grade_ok, administrative_ok, documents_complete, correlatives_ok) "
                    + "values (?, 3, 'PROMOTES', 8.75, true, true, true, true, true)", closureId);
            jdbc.update("insert into commission_closure_student (closure_id, enrollment_id, final_condition) values (?, 4, 'GOES_TO_FINAL')",
                    closureId);

            assertThat(jdbc.queryForObject(
                    "select promotion_grade from commission_closure_student where enrollment_id = 3", java.math.BigDecimal.class))
                    .isEqualByComparingTo("8.75");
            assertThat(jdbc.queryForObject(
                    "select attendance_ok from commission_closure_student where enrollment_id = 4", Boolean.class)).isNull();
            assertThatThrownBy(() -> jdbc.update(
                    "insert into commission_closure_student (closure_id, enrollment_id, final_condition) values (?, 4, 'FREE')",
                    closureId)).hasMessageContaining("uq_commission_closure_student");
        }
    }

    @Test
    void reportNumberSequenceIncreasesByOne() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("205")) {
            JdbcTemplate jdbc = migrated(dataSource);

            long first = jdbc.queryForObject("select nextval('closure_report_number_seq')", Long.class);
            long second = jdbc.queryForObject("select nextval('closure_report_number_seq')", Long.class);

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(2);
        }
    }
}
