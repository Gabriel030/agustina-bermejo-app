package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the review status migration, loads rows created before the gate and migrates to the latest version")
@Tag("migration")
class ImportedGraduationReviewCorrectionMigrationTest {

    private static final String VERSION_WITH_REVIEW_STATUS = "443";

    private static final long IMPORTED_NOT_REQUIRED_WITHOUT_CERTIFICATE = 301;
    private static final long IMPORTED_NOT_REQUIRED_WITH_VALID_CERTIFICATE = 302;
    private static final long IMPORTED_NOT_REQUIRED_WITH_REVOKED_CERTIFICATE = 303;
    private static final long IMPORTED_ALREADY_PENDING_REVIEW = 304;
    private static final long IMPORTED_ALREADY_ENABLED = 305;
    private static final long APPROVED_BY_DIRECTOR = 306;
    private static final long REJECTED_WITHOUT_DECIDER = 307;
    private static final long REQUESTED_WITHOUT_DECIDER = 308;
    private static final long IMPORTED_WITH_CERTIFICATE_OF_ANOTHER_ENROLLMENT = 309;
    private static final long CERTIFICATE_OF_ANOTHER_ENROLLMENT = 310;

    @Test
    void correctionMovesOnlyImportedRowsStillNotRequired() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(VERSION_WITH_REVIEW_STATUS)) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table graduation_request disable trigger all");
            jdbc.execute("alter table graduation_certificate disable trigger all");
            insertGraduationRequest(jdbc, IMPORTED_NOT_REQUIRED_WITHOUT_CERTIFICATE, "APPROVED", null, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, IMPORTED_NOT_REQUIRED_WITH_VALID_CERTIFICATE, "APPROVED", null, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, IMPORTED_NOT_REQUIRED_WITH_REVOKED_CERTIFICATE, "APPROVED", null, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, IMPORTED_ALREADY_PENDING_REVIEW, "APPROVED", null, "PENDING_REVIEW");
            insertGraduationRequest(jdbc, IMPORTED_ALREADY_ENABLED, "APPROVED", null, "ENABLED");
            insertGraduationRequest(jdbc, APPROVED_BY_DIRECTOR, "APPROVED", 7L, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, REJECTED_WITHOUT_DECIDER, "REJECTED", null, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, REQUESTED_WITHOUT_DECIDER, "REQUESTED", null, "NOT_REQUIRED");
            insertGraduationRequest(jdbc, IMPORTED_WITH_CERTIFICATE_OF_ANOTHER_ENROLLMENT, "APPROVED", null, "NOT_REQUIRED");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP011", IMPORTED_NOT_REQUIRED_WITH_VALID_CERTIFICATE, "VALID");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP012", IMPORTED_NOT_REQUIRED_WITH_REVOKED_CERTIFICATE, "REVOKED");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP013", CERTIFICATE_OF_ANOTHER_ENROLLMENT, "VALID");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            Map<Long, String> reviewStatusByEnrollment = jdbc.query(
                    "select program_enrollment_id, certificate_review_status from graduation_request",
                    rs -> {
                        Map<Long, String> result = new HashMap<>();
                        while (rs.next()) {
                            result.put(rs.getLong(1), rs.getString(2));
                        }
                        return result;
                    });
            assertThat(reviewStatusByEnrollment).containsOnly(
                    Map.entry(IMPORTED_NOT_REQUIRED_WITHOUT_CERTIFICATE, "PENDING_REVIEW"),
                    Map.entry(IMPORTED_NOT_REQUIRED_WITH_VALID_CERTIFICATE, "ENABLED"),
                    Map.entry(IMPORTED_NOT_REQUIRED_WITH_REVOKED_CERTIFICATE, "ENABLED"),
                    Map.entry(IMPORTED_ALREADY_PENDING_REVIEW, "PENDING_REVIEW"),
                    Map.entry(IMPORTED_ALREADY_ENABLED, "ENABLED"),
                    Map.entry(APPROVED_BY_DIRECTOR, "NOT_REQUIRED"),
                    Map.entry(REJECTED_WITHOUT_DECIDER, "NOT_REQUIRED"),
                    Map.entry(REQUESTED_WITHOUT_DECIDER, "NOT_REQUIRED"),
                    Map.entry(IMPORTED_WITH_CERTIFICATE_OF_ANOTHER_ENROLLMENT, "PENDING_REVIEW"));
        }
    }

    private static void insertGraduationRequest(JdbcTemplate jdbc, long enrollmentId, String status, Long decidedById, String reviewStatus) {
        jdbc.update(
                "insert into graduation_request (program_enrollment_id, status, requested_at, decided_by_id, certificate_review_status) values (?, ?, now(), ?, ?)",
                enrollmentId, status, decidedById, reviewStatus);
    }

    private static void insertCertificate(JdbcTemplate jdbc, String code, long enrollmentId, String status) {
        jdbc.update(
                "insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, revoked_at, revoked_by_id, revocation_reason) "
                        + "values (?, 'PROGRAM', ?, 1, ?, current_date, now(), 1, '{}'::jsonb, 'default', repeat('a', 64), 'path', case when ? = 'REVOKED' then now() end, case when ? = 'REVOKED' then 1 end, case when ? = 'REVOKED' then 'motivo de prueba' end)",
                code, enrollmentId, status, status, status, status);
    }
}
