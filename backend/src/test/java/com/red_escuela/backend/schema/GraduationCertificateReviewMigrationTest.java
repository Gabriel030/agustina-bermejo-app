package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the version before the review status migration, loads historical rows and migrates to the latest version")
@Tag("migration")
class GraduationCertificateReviewMigrationTest {

    private static final long ENROLLMENT_WITH_VALID_CERTIFICATE = 101;
    private static final long ENROLLMENT_WITH_REVOKED_CERTIFICATE = 102;
    private static final long ENROLLMENT_IMPORTED_WITHOUT_CERTIFICATE = 103;
    private static final long ENROLLMENT_APPROVED_BY_DIRECTOR = 104;
    private static final long ENROLLMENT_REJECTED = 105;
    private static final long ENROLLMENT_APPROVED_BY_DIRECTOR_WITH_CERTIFICATE = 106;
    private static final long ENROLLMENT_IMPORTED_WITH_CERTIFICATE_OF_ANOTHER = 107;
    private static final long ENROLLMENT_REQUESTED = 108;
    private static final long ENROLLMENT_CERTIFICATE_OF_ANOTHER = 109;

    @Test
    void backfillClassifiesEachExistingGraduationRequest() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(versionBeforeReviewMigration())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table graduation_request disable trigger all");
            jdbc.execute("alter table graduation_certificate disable trigger all");
            insertGraduationRequest(jdbc, ENROLLMENT_WITH_VALID_CERTIFICATE, "APPROVED", null);
            insertGraduationRequest(jdbc, ENROLLMENT_WITH_REVOKED_CERTIFICATE, "APPROVED", null);
            insertGraduationRequest(jdbc, ENROLLMENT_IMPORTED_WITHOUT_CERTIFICATE, "APPROVED", null);
            insertGraduationRequest(jdbc, ENROLLMENT_APPROVED_BY_DIRECTOR, "APPROVED", 7L);
            insertGraduationRequest(jdbc, ENROLLMENT_REJECTED, "REJECTED", null);
            insertGraduationRequest(jdbc, ENROLLMENT_APPROVED_BY_DIRECTOR_WITH_CERTIFICATE, "APPROVED", 7L);
            insertGraduationRequest(jdbc, ENROLLMENT_IMPORTED_WITH_CERTIFICATE_OF_ANOTHER, "APPROVED", null);
            insertGraduationRequest(jdbc, ENROLLMENT_REQUESTED, "REQUESTED", null);
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP001", ENROLLMENT_WITH_VALID_CERTIFICATE, "VALID");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP002", ENROLLMENT_WITH_REVOKED_CERTIFICATE, "REVOKED");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP003", ENROLLMENT_APPROVED_BY_DIRECTOR_WITH_CERTIFICATE, "VALID");
            insertCertificate(jdbc, "0123456789ABCDEFGHJKMNP004", ENROLLMENT_CERTIFICATE_OF_ANOTHER, "VALID");

            migrateToLatest(dataSource);

            Map<Long, String> reviewStatusByEnrollment = jdbc.query(
                    "select program_enrollment_id, certificate_review_status from graduation_request",
                    rs -> {
                        Map<Long, String> result = new java.util.HashMap<>();
                        while (rs.next()) {
                            result.put(rs.getLong(1), rs.getString(2));
                        }
                        return result;
                    });
            assertThat(reviewStatusByEnrollment).containsOnly(
                    Map.entry(ENROLLMENT_WITH_VALID_CERTIFICATE, "ENABLED"),
                    Map.entry(ENROLLMENT_WITH_REVOKED_CERTIFICATE, "ENABLED"),
                    Map.entry(ENROLLMENT_IMPORTED_WITHOUT_CERTIFICATE, "PENDING_REVIEW"),
                    Map.entry(ENROLLMENT_APPROVED_BY_DIRECTOR, "NOT_REQUIRED"),
                    Map.entry(ENROLLMENT_REJECTED, "NOT_REQUIRED"),
                    Map.entry(ENROLLMENT_APPROVED_BY_DIRECTOR_WITH_CERTIFICATE, "NOT_REQUIRED"),
                    Map.entry(ENROLLMENT_IMPORTED_WITH_CERTIFICATE_OF_ANOTHER, "PENDING_REVIEW"),
                    Map.entry(ENROLLMENT_REQUESTED, "NOT_REQUIRED"));
            assertThat(jdbc.queryForObject(
                    "select count(*) from graduation_request where certificate_reviewed_by_id is not null or certificate_reviewed_at is not null",
                    Integer.class)).isZero();
        }
    }

    @Test
    void checksAcceptOnlyCoherentReviewData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table graduation_request disable trigger all");

            insertGraduationRequest(jdbc, 201, "APPROVED", null);
            assertThat(reviewStatusOf(jdbc, 201)).isEqualTo("NOT_REQUIRED");

            assertThat(accepts(jdbc, 202, "ENABLED", null, false)).isTrue();
            assertThat(accepts(jdbc, 203, "ENABLED", 7L, true)).isTrue();
            assertThat(accepts(jdbc, 204, "DISABLED", 7L, true)).isTrue();
            assertThat(accepts(jdbc, 205, "PENDING_REVIEW", null, false)).isTrue();

            assertThat(accepts(jdbc, 206, "PENDING_REVIEW", 7L, true)).isFalse();
            assertThat(accepts(jdbc, 207, "NOT_REQUIRED", null, true)).isFalse();
            assertThat(accepts(jdbc, 208, "NOT_REQUIRED", 7L, false)).isFalse();
            assertThat(accepts(jdbc, 209, "ENABLED", 7L, false)).isFalse();
            assertThat(accepts(jdbc, 210, "DISABLED", null, true)).isFalse();
            assertThat(accepts(jdbc, 211, "UNKNOWN", null, false)).isFalse();
        }
    }

    @Test
    void reviewPermissionIsFixedToDirectorAndReviewAuditValuesAreAccepted() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(latestMigrationVersion())) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThat(jdbc.queryForMap(
                    "select fixed_to_director, sensitive from permission where code = 'graduation.review_imported_certificate'"))
                    .containsEntry("fixed_to_director", true)
                    .containsEntry("sensitive", true);
            assertThat(jdbc.queryForObject(
                    "select count(*) from role_permission rp join permission p on p.id = rp.permission_id where p.code = 'graduation.review_imported_certificate'",
                    Integer.class)).isZero();
            assertThat(jdbc.update(
                    "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'GRADUATION_CERTIFICATE_REVIEW_DISABLED', 'GRADUATION_REQUEST', 1)"))
                    .isEqualTo(1);
            assertThat(jdbc.update(
                    "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'GRADUATION_CERTIFICATE_REVIEW_ENABLED', 'GRADUATION_CERTIFICATE', 1)"))
                    .isEqualTo(1);
            assertThat(jdbc.update(
                    "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'GRADUATION_CERTIFICATE_PDF_MISMATCH', 'GRADUATION_REQUEST', 1)"))
                    .isEqualTo(1);
            assertThat(jdbc.update(
                    "insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'STAFF_WITHDRAWN', 'PERSON', 1)"))
                    .isEqualTo(1);
        }
    }

    private static boolean accepts(JdbcTemplate jdbc, long enrollmentId, String reviewStatus, Long reviewerId, boolean withDate) {
        try {
            jdbc.update(
                    "insert into graduation_request (program_enrollment_id, status, requested_at, certificate_review_status, certificate_reviewed_by_id, certificate_reviewed_at) "
                            + "values (?, 'APPROVED', now(), ?, ?, " + (withDate ? "now()" : "null") + ")",
                    enrollmentId, reviewStatus, reviewerId);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String reviewStatusOf(JdbcTemplate jdbc, long enrollmentId) {
        return jdbc.queryForObject("select certificate_review_status from graduation_request where program_enrollment_id = ?", String.class, enrollmentId);
    }

    private static void insertGraduationRequest(JdbcTemplate jdbc, long enrollmentId, String status, Long decidedById) {
        jdbc.update(
                "insert into graduation_request (program_enrollment_id, status, requested_at, decided_by_id) values (?, ?, now(), ?)",
                enrollmentId, status, decidedById);
    }

    private static void insertCertificate(JdbcTemplate jdbc, String code, long enrollmentId, String status) {
        jdbc.update(
                "insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, revoked_at, revoked_by_id, revocation_reason) "
                        + "values (?, 'PROGRAM', ?, 1, ?, current_date, now(), 1, '{}'::jsonb, 'default', repeat('a', 64), 'path', case when ? = 'REVOKED' then now() end, case when ? = 'REVOKED' then 1 end, case when ? = 'REVOKED' then 'motivo de prueba' end)",
                code, enrollmentId, status, status, status, status);
    }

    private static void migrateToLatest(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    private static String versionBeforeReviewMigration() {
        int reviewVersion = Integer.parseInt(versionOfReviewMigration());
        return migrationVersions().keySet().stream()
                .map(Integer::parseInt)
                .filter(version -> version < reviewVersion)
                .max(java.util.Comparator.naturalOrder())
                .map(String::valueOf)
                .orElseThrow();
    }

    private static String versionOfReviewMigration() {
        return migrationVersions().entrySet().stream()
                .filter(entry -> entry.getValue().equals("graduation_certificate_review_status"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }

    private static String latestMigrationVersion() {
        return migrationVersions().keySet().stream().max(java.util.Comparator.comparingInt(Integer::parseInt)).orElseThrow();
    }

    private static Map<String, String> migrationVersions() {
        try {
            return Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql"))
                    .map(resource -> resource.getFilename())
                    .collect(Collectors.toMap(
                            name -> name.substring(1, name.indexOf("__")),
                            name -> name.substring(name.indexOf("__") + 2, name.length() - ".sql".length())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
