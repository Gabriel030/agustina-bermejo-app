package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SlowTest(reason = "Levanta su propio Postgres y migra por etapas para probar el CHECK de alcance de la promoción de pago anual antes y después de V154")
@Tag("migration")
class AnnualUpfrontPromotionScopeMigrationTest {

    private static final String CONSTRAINT = "ck_discount_definition_scope_by_type";

    private static void migrateTo(DriverManagerDataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load().migrate();
    }

    private static void insertDefinition(JdbcTemplate jdbc, String type, String invoiceType, Long programId, Long cohortId, Long courseId, String window, Integer quota) {
        jdbc.update("insert into discount_definition (name, discount_type, applicable_invoice_type, calculation, available_from, priority, program_id, program_cohort_id, course_id, "
                        + "enrollment_window_from, enrollment_window_to, quota, status, created_by_process, created_at, direct_publication) "
                        + "values ('Seed', ?, ?, '{\"type\": \"PERCENTAGE\", \"value\": 10}'::jsonb, date '2027-01-01', 1, ?, ?, ?, "
                        + (window == null ? "null, null" : "date '2027-01-01', date '2027-03-31'") + ", ?, 'ACTIVE', 'test', now(), true)",
                type, invoiceType, programId, cohortId, courseId, quota);
    }

    @Test
    @Tag("D-298")
    @Tag("D-240")
    void annualUpfrontPromotionCarriesProgramWindowAndQuotaOnlyAfterTheScopeCheckIsReplaced() {
        try (MigrationTestDatabase database = MigrationTestDatabase.migratedTo("153")) {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.update("insert into address default values");
            Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
            jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
            Long personId = jdbc.queryForObject("select max(id) from person", Long.class);
            jdbc.update("insert into term (year_number, status, start_date, end_date) values (2027, true, timestamp '2027-03-01 00:00:00', timestamp '2027-12-15 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS')");
            Long programId = jdbc.queryForObject("select max(id) from program", Long.class);
            jdbc.update("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS')", termId);
            Long courseId = jdbc.queryForObject("select max(id) from course", Long.class);
            jdbc.update("insert into program_year_billing_template (program_id, term_id, created_by_id, created_at) values (?, ?, ?, timestamp '2027-01-05 10:00:00')", programId, termId, personId);
            Long templateId = jdbc.queryForObject("select max(id) from program_year_billing_template", Long.class);
            jdbc.update("insert into program_cohort (template_id, intake, admission_start, admission_end, cursada_start, created_by_id, created_at) "
                    + "values (?, 'FIRST', date '2027-01-01', date '2027-02-01', date '2027-03-01', ?, now())", templateId, personId);
            Long cohortId = jdbc.queryForObject("select max(id) from program_cohort", Long.class);
            insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", null, null, null, null, null);

            assertThatThrownBy(() -> insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", programId, null, null, "window", 1))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_discount_definition_scope_only_cohort_promotion");

            migrateTo(database, "154");

            insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", programId, null, null, "window", 1);
            insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", null, null, null, null, 5);
            insertDefinition(jdbc, "COHORT_PROMOTION", "ENROLLMENT_FEE", programId, cohortId, null, "window", 3);
            assertThat(jdbc.queryForObject("select count(*) from discount_definition where discount_type = 'ANNUAL_UPFRONT' and quota is not null", Long.class)).isEqualTo(2);
            assertThatThrownBy(() -> insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", null, null, courseId, null, 1))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CONSTRAINT);
            assertThatThrownBy(() -> insertDefinition(jdbc, "ANNUAL_UPFRONT", "ANNUAL_UPFRONT", programId, cohortId, null, null, 1))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CONSTRAINT);
            assertThatThrownBy(() -> insertDefinition(jdbc, "PAYMENT_METHOD", "MONTHLY_INSTALLMENT", programId, null, null, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CONSTRAINT);
            assertThatThrownBy(() -> insertDefinition(jdbc, "PAYMENT_METHOD", "MONTHLY_INSTALLMENT", null, null, null, "window", null))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CONSTRAINT);
            assertThatThrownBy(() -> insertDefinition(jdbc, "PAYMENT_METHOD", "MONTHLY_INSTALLMENT", null, null, null, null, 2))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(CONSTRAINT);
        }
    }
}
