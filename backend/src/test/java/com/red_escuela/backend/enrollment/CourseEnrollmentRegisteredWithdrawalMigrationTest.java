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
class CourseEnrollmentRegisteredWithdrawalMigrationTest {

    private static final String CHECK = "ck_course_enrollment_registered_withdrawal_has_effective_date";

    @Test
    void registeredWithdrawalWithoutEffectiveDateIsAcceptedBeforeV479AndRejectedAfter() {
        try (MigrationTestDatabase before = MigrationTestDatabase.migratedTo("478");
             MigrationTestDatabase after = MigrationTestDatabase.migratedTo("479")) {
            JdbcTemplate beforeJdbc = detached(before);
            JdbcTemplate afterJdbc = detached(after);

            assertThatCode(() -> insertCourseEnrollment(beforeJdbc, 1, "WITHDRAWN", null, "2026-03-01")).doesNotThrowAnyException();
            assertThatThrownBy(() -> insertCourseEnrollment(afterJdbc, 1, "WITHDRAWN", null, "2026-03-01"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CHECK);
        }
    }

    @Test
    void waitlistRemovalWithoutRegisteredWithdrawalIsStillAccepted() {
        try (MigrationTestDatabase after = MigrationTestDatabase.migratedTo("479")) {
            JdbcTemplate jdbc = detached(after);

            assertThatCode(() -> insertCourseEnrollment(jdbc, 1, "WITHDRAWN", null, null)).doesNotThrowAnyException();
        }
    }

    @Test
    void registeredWithdrawalWithEffectiveDateAndOtherStatusesAreAcceptedAndTheCheckStaysNotValid() {
        try (MigrationTestDatabase after = MigrationTestDatabase.migratedTo("479")) {
            JdbcTemplate jdbc = detached(after);

            assertThatCode(() -> {
                insertCourseEnrollment(jdbc, 1, "WITHDRAWN", "2026-03-01", "2026-03-02");
                insertCourseEnrollment(jdbc, 2, "ENROLLED", null, null);
                insertCourseEnrollment(jdbc, 3, "WAITLISTED", null, null);
            }).doesNotThrowAnyException();
            assertThat(jdbc.queryForObject("select convalidated from pg_constraint where conname = ?", Boolean.class, CHECK)).isFalse();
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

    private void insertCourseEnrollment(JdbcTemplate jdbc, long studentId, String status, String effectiveDate, String registeredOn) {
        jdbc.update("insert into course_enrollment (id, student_id, course_id, status, enrollment_date, withdrawal_effective_date, withdrawal_registered_on) "
                        + "values (?, ?, 1, ?, now(), cast(? as date), cast(? as date))",
                studentId, studentId, status, effectiveDate, registeredOn);
    }
}
