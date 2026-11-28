package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class EnrollmentIntegrityConstraintsMigrationTest {

    private static final String PROGRAM_WITHDRAWN_CHECK = "ck_program_enrollment_withdrawn_has_effective_date";
    private static final String PROGRAM_COMPLETED_CHECK = "ck_program_enrollment_completed_has_completion_date";

    @Test
    void withdrawnProgramEnrollmentWithoutEffectiveDateIsAcceptedBeforeV476AndRejectedAfter() {
        try (MigrationTestDatabase before = MigrationTestDatabase.migratedTo("475");
             MigrationTestDatabase after = MigrationTestDatabase.migratedTo("476")) {
            JdbcTemplate beforeJdbc = detached(before);
            JdbcTemplate afterJdbc = detached(after);

            assertThatCode(() -> insertProgramEnrollment(beforeJdbc, 1, "WITHDRAWN", null, null)).doesNotThrowAnyException();
            assertThatThrownBy(() -> insertProgramEnrollment(afterJdbc, 1, "WITHDRAWN", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(PROGRAM_WITHDRAWN_CHECK);
            assertThatCode(() -> insertProgramEnrollment(afterJdbc, 2, "WITHDRAWN", "2026-03-01", null)).doesNotThrowAnyException();
        }
    }

    @Test
    void completedProgramEnrollmentWithoutCompletionDateIsAcceptedBeforeV476AndRejectedAfter() {
        try (MigrationTestDatabase before = MigrationTestDatabase.migratedTo("475");
             MigrationTestDatabase after = MigrationTestDatabase.migratedTo("476")) {
            JdbcTemplate beforeJdbc = detached(before);
            JdbcTemplate afterJdbc = detached(after);

            assertThatCode(() -> insertProgramEnrollment(beforeJdbc, 1, "COMPLETED", null, null)).doesNotThrowAnyException();
            assertThatThrownBy(() -> insertProgramEnrollment(afterJdbc, 1, "COMPLETED", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(PROGRAM_COMPLETED_CHECK);
            assertThatCode(() -> insertProgramEnrollment(afterJdbc, 2, "COMPLETED", null, "2026-03-01 10:00:00")).doesNotThrowAnyException();
        }
    }

    @Test
    void otherStatusesDoNotNeedTheDatesAndTheChecksStayNotValid() {
        try (MigrationTestDatabase after = MigrationTestDatabase.migratedTo("476")) {
            JdbcTemplate jdbc = detached(after);

            assertThatCode(() -> {
                insertProgramEnrollment(jdbc, 1, "ENROLLED", null, null);
                insertProgramEnrollment(jdbc, 2, "PAUSED", null, null);
            }).doesNotThrowAnyException();
            assertThat(jdbc.queryForList("select convalidated from pg_constraint where conname in (?, ?)", Boolean.class,
                    PROGRAM_WITHDRAWN_CHECK, PROGRAM_COMPLETED_CHECK)).containsExactly(false, false);
        }
    }

    private JdbcTemplate detached(MigrationTestDatabase dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String[]> foreignKeys = jdbc.query(
                "select conrelid::regclass::text, conname from pg_constraint where contype = 'f'",
                (rs, row) -> new String[] {rs.getString(1), rs.getString(2)});
        for (String[] foreignKey : foreignKeys) {
            jdbc.execute("alter table " + foreignKey[0] + " drop constraint " + foreignKey[1]);
        }
        return jdbc;
    }

    private void insertProgramEnrollment(JdbcTemplate jdbc, long studentId, String status, String withdrawalDate, String completionDate) {
        jdbc.update("insert into program_enrollment (id, student_id, program_id, status, enrollment_date, withdrawal_effective_date, completion_date) "
                        + "values (?, ?, 1, ?, now(), cast(? as date), cast(? as timestamp))",
                studentId, studentId, status, withdrawalDate, completionDate);
    }

    private void insertCourseEnrollment(JdbcTemplate jdbc, long studentId, String status, String withdrawalDate) {
        jdbc.update("insert into course_enrollment (id, student_id, course_id, status, enrollment_date, withdrawal_effective_date) "
                        + "values (?, ?, 1, ?, now(), cast(? as date))",
                studentId, studentId, status, withdrawalDate);
    }
}
