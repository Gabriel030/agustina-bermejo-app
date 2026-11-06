package com.red_escuela.backend.graduation.certificate.verification;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class CertificateVerificationCodeMigrationTest {

    private static final String LEGACY_GRADUATION = "7QK3M9XH2VBN4T8R";
    private static final String LEGACY_REGULAR = "K2M8Q4RZ7W5T9HXE";
    private static final String CODE_FORMAT = "[0-9A-HJKMNP-TV-Z]{26}";

    private void seedLegacyCertificates(JdbcTemplate jdbc) {
        jdbc.execute("alter table graduation_certificate disable trigger all");
        jdbc.execute("alter table regular_student_certificate disable trigger all");
        jdbc.execute("alter table regular_certificate_request disable trigger all");
        jdbc.update("insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, issued_at, "
                + "issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path, registry_number) values (?, 'PROGRAM', 11, 1, 'VALID', "
                + "'2026-07-15', '2026-11-05 10:00:00', 1, '{\"holderFullName\":\"A\"}'::jsonb, 'BUILT_IN_V1', repeat('a', 64), ?, '1284')",
                LEGACY_GRADUATION, LEGACY_GRADUATION + ".pdf");
        jdbc.update("insert into graduation_certificate (code, kind, course_enrollment_id, student_id, status, graduation_date, issued_at, "
                + "issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) values ('AAAAAAAAAAAAAAAA', 'COURSE', 12, 2, 'REVOKED', "
                + "'2026-07-15', '2026-11-05 10:00:00', 1, '{}'::jsonb, 'BUILT_IN_V1', repeat('b', 64), 'AAAAAAAAAAAAAAAA.pdf')");
        jdbc.update("insert into regular_certificate_request (student_id, kind, program_enrollment_id, requested_by_id, requested_at, "
                + "channel, outcome) values (1, 'PROGRAM', 11, 1, now(), 'STAFF', 'ISSUED')");
        jdbc.update("insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, expires_at, "
                + "validity_days, issued_by_id, issue_channel, request_id, billing_mode_at_issue, standing_basis, snapshot, template_ref, "
                + "pdf_sha256, pdf_path) values (?, 'PROGRAM', 11, 1, 'VALID', now(), now(), 30, 1, 'STAFF', "
                + "(select max(id) from regular_certificate_request), 'OFF', 'AUTOMATIC', '{}'::jsonb, 'REGULAR_BUILT_IN_V1', "
                + "repeat('c', 64), ?)", LEGACY_REGULAR, LEGACY_REGULAR + ".pdf");
    }

    private void migrate(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @Test
    void existingCertificatesOfBothTypesGetNewHighEntropyCodesAndKeepTheirData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("193")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedLegacyCertificates(jdbc);

            migrate(dataSource);

            List<String> graduationCodes = jdbc.queryForList("select code from graduation_certificate", String.class);
            List<String> regularCodes = jdbc.queryForList("select code from regular_student_certificate", String.class);
            assertThat(graduationCodes).hasSize(2).allMatch(code -> code.matches(CODE_FORMAT))
                    .doesNotContain(LEGACY_GRADUATION, "AAAAAAAAAAAAAAAA");
            assertThat(regularCodes).hasSize(1).allMatch(code -> code.matches(CODE_FORMAT)).doesNotContain(LEGACY_REGULAR);
            Set<String> all = new HashSet<>(graduationCodes);
            all.addAll(regularCodes);
            assertThat(all).hasSize(3);
            assertThat(jdbc.queryForObject("select registry_number from graduation_certificate where status = 'VALID'", String.class))
                    .isEqualTo("1284");
            assertThat(jdbc.queryForObject("select count(*) from graduation_certificate where status = 'REVOKED'", Integer.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void storedPdfsAreInvalidatedSoTheyRegenerateWithTheNewCode() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("193")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seedLegacyCertificates(jdbc);

            migrate(dataSource);

            assertThat(jdbc.queryForList("select pdf_sha256 from graduation_certificate", String.class))
                    .containsOnly("0".repeat(64));
            assertThat(jdbc.queryForList("select pdf_sha256 from regular_student_certificate", String.class))
                    .containsOnly("0".repeat(64));
        }
    }

    @Test
    void regeneratedCodesDoNotDeriveFromIdsOrFromTheOldCodes() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("193")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table graduation_certificate disable trigger all");
            for (int i = 1; i <= 200; i++) {
                jdbc.update("insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, "
                        + "issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) values (?, 'PROGRAM', ?, 1, 'REVOKED', "
                        + "'2026-07-15', '2026-11-05 10:00:00', 1, '{}'::jsonb, 'BUILT_IN_V1', repeat('a', 64), 'x.pdf')",
                        String.format("%016d", i), i);
            }

            migrate(dataSource);

            List<String> codes = jdbc.queryForList("select code from graduation_certificate order by id", String.class);
            assertThat(new HashSet<>(codes)).hasSize(200);
            assertThat(codes).allMatch(code -> code.matches(CODE_FORMAT));
            Set<Character> firstCharacters = new HashSet<>();
            codes.forEach(code -> firstCharacters.add(code.charAt(0)));
            assertThat(firstCharacters.size()).isGreaterThan(10);
            Set<Character> allCharacters = new HashSet<>();
            codes.forEach(code -> code.chars().forEach(c -> allCharacters.add((char) c)));
            assertThat(allCharacters).hasSize(32);
        }
    }

    @Test
    void legacySixteenCharacterCodesAreRejectedAfterTheMigration() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("193")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource);
            jdbc.execute("alter table graduation_certificate disable trigger all");

            assertThatThrownBy(() -> jdbc.update("insert into graduation_certificate (code, kind, program_enrollment_id, student_id, "
                    + "status, graduation_date, issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) values "
                    + "('7QK3M9XH2VBN4T8R', 'PROGRAM', 1, 1, 'VALID', '2026-07-15', now(), 1, '{}'::jsonb, 'B', repeat('a', 64), 'x.pdf')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void codesStayUniqueAcrossRowsOfTheSameTable() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("193")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            migrate(dataSource);
            jdbc.execute("alter table graduation_certificate disable trigger all");
            String insert = "insert into graduation_certificate (code, kind, program_enrollment_id, student_id, status, graduation_date, "
                    + "issued_at, issued_by_id, snapshot, template_ref, pdf_sha256, pdf_path) values (?, 'PROGRAM', ?, 1, 'REVOKED', "
                    + "'2026-07-15', now(), 1, '{}'::jsonb, 'B', repeat('a', 64), 'x.pdf')";
            jdbc.update(insert, "7QK3M9XH2VBN4T8R5W6YZ0A1B2", 1);

            assertThatThrownBy(() -> jdbc.update(insert, "7QK3M9XH2VBN4T8R5W6YZ0A1B2", 2))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
