package com.red_escuela.backend.graduation.certificate.regular;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class RegularCertificateRequestPendingUniqueMigrationTest {

    @Test
    void duplicatePendingRequestsAreAcceptedBeforeV475() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("474")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertRequest(jdbc, "PROGRAM", 1L, null, "PENDING_STAFF");

            assertThatCode(() -> insertRequest(jdbc, "PROGRAM", 1L, null, "PENDING_STAFF")).doesNotThrowAnyException();
        }
    }

    @Test
    void secondPendingRequestOfSameEnrollmentIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("475")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertRequest(jdbc, "PROGRAM", 1L, null, "PENDING_STAFF");
            insertRequest(jdbc, "COURSE", null, 1L, "PENDING_STAFF");

            assertThatThrownBy(() -> insertRequest(jdbc, "PROGRAM", 1L, null, "PENDING_STAFF")).isInstanceOf(DuplicateKeyException.class);
            assertThatThrownBy(() -> insertRequest(jdbc, "COURSE", null, 1L, "PENDING_STAFF")).isInstanceOf(DuplicateKeyException.class);
        }
    }

    @Test
    void resolvedRequestsAndOtherEnrollmentsDoNotCollide() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("475")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            insertRequest(jdbc, "PROGRAM", 1L, null, "PENDING_STAFF");
            insertRequest(jdbc, "PROGRAM", 1L, null, "ISSUED");
            insertRequest(jdbc, "PROGRAM", 1L, null, "REJECTED");
            insertRequest(jdbc, "PROGRAM", 1L, null, "ISSUED");
            insertRequest(jdbc, "PROGRAM", 2L, null, "PENDING_STAFF");

            assertThat(jdbc.queryForObject("select count(*) from regular_certificate_request", Integer.class)).isEqualTo(5);
        }
    }

    private void insertRequest(JdbcTemplate jdbc, String kind, Long programEnrollmentId, Long courseEnrollmentId, String outcome) {
        jdbc.execute("alter table regular_certificate_request disable trigger all");
        jdbc.update("insert into regular_certificate_request (student_id, kind, program_enrollment_id, course_enrollment_id, requested_by_id, "
                        + "requested_at, channel, outcome) values (1, ?, ?, ?, 1, now(), 'STAFF', ?)",
                kind, programEnrollmentId, courseEnrollmentId, outcome);
        jdbc.execute("alter table regular_certificate_request enable trigger all");
    }
}
