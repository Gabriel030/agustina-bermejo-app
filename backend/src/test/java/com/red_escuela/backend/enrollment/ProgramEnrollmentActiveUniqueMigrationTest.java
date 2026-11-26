package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class ProgramEnrollmentActiveUniqueMigrationTest {

    @Test
    void duplicateActiveEnrollmentsAreAcceptedBeforeV474() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("473")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertEnrollment(jdbc, 1, 1, "ENROLLED");

            assertThatCode(() -> insertEnrollment(jdbc, 1, 1, "ENROLLED")).doesNotThrowAnyException();
        }
    }

    @Test
    void secondEnrolledOrPausedEnrollmentOfSameStudentAndProgramIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("474")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertEnrollment(jdbc, 1, 1, "ENROLLED");

            assertThatThrownBy(() -> insertEnrollment(jdbc, 1, 1, "ENROLLED")).isInstanceOf(DuplicateKeyException.class);
            assertThatThrownBy(() -> insertEnrollment(jdbc, 1, 1, "PAUSED")).isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Test
    void closedEnrollmentsAndOtherStudentsOrProgramsDoNotCollide() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("474")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertEnrollment(jdbc, 1, 1, "WITHDRAWN");
            insertEnrollment(jdbc, 1, 1, "COMPLETED");
            insertEnrollment(jdbc, 1, 1, "WITHDRAWN");
            insertEnrollment(jdbc, 1, 1, "ENROLLED");
            insertEnrollment(jdbc, 2, 1, "ENROLLED");
            insertEnrollment(jdbc, 1, 2, "ENROLLED");

            assertThat(jdbc.queryForObject("select count(*) from program_enrollment", Integer.class)).isEqualTo(6);
        }
    }

    private void insertEnrollment(JdbcTemplate jdbc, long studentId, long programId, String status) {
        jdbc.execute("alter table program_enrollment disable trigger all");
        jdbc.update("insert into program_enrollment (student_id, program_id, status, enrollment_date) values (?, ?, ?, now())",
                studentId, programId, status);
        jdbc.execute("alter table program_enrollment enable trigger all");
    }
}
