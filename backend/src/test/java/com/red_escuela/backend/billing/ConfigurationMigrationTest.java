package com.red_escuela.backend.billing;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationMigrationTest {

    @Test
    @Tag("D-095")
    @Tag("D-210")
    @Tag("D-118")
    void versionOneCopiesExactlyWhatExistsToday() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("93").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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
            jdbc.update("insert into program_year_billing_template (program_id, term_id, recurring_fee_invoices_count, enrollment_fee_count, first_enrollment_fee_month, "
                    + "second_enrollment_fee_month, enrollment_fee_lead_months, late_enrollment_fee_due_days, recurring_fee_due_days, withdraw_suspended_at_term_end, "
                    + "pays_while_paused, blocks_enrollment, blocks_exam_registration, blocks_certificates, require_enrollment_fee_for_final_exam_only_students, "
                    + "created_by_id, created_at) values (?, ?, 10, 2, 2, 7, 1, 12, 15, true, true, true, false, true, true, ?, timestamp '2027-01-05 10:00:00')",
                    programId, termId, personId);
            Long templateId = jdbc.queryForObject("select max(id) from program_year_billing_template", Long.class);
            jdbc.update("insert into program_cohort (template_id, intake, admission_start, admission_end, cursada_start, created_by_id, created_at, entry_fee_discount, enrollment_fee_month) "
                    + "values (?, 'FIRST', date '2027-01-01', date '2027-02-01', date '2027-03-01', ?, now(), 0.15, 2)", templateId, personId);
            Long cohortId = jdbc.queryForObject("select max(id) from program_cohort", Long.class);
            jdbc.update("insert into program_cohort (template_id, intake, admission_start, admission_end, cursada_start, created_by_id, created_at, entry_fee_discount, enrollment_fee_month) "
                    + "values (?, 'SECOND', date '2027-01-01', date '2027-02-01', date '2027-03-01', ?, now(), 0, 2)", templateId, personId);
            jdbc.update("insert into late_payment_settings (grace_period_days, surcharge_percentage, suspension_after_days, suspension_after_invoice_count, "
                    + "effective_from, created_by_id, created_at) "
                    + "values (5, 0.0500, 30, 2, date '2020-01-01', ?, now())", personId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("94").load().migrate();

            Map<String, Object> year = jdbc.queryForMap("select config_values::text as v, status, effective_from, direct_publication from configuration_version where scope = 'YEAR_TERMS'");
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'YEAR_TERMS'", Long.class)).isEqualTo(1);
            assertThat(year.get("status")).isEqualTo("EFFECTIVE");
            assertThat(year.get("direct_publication")).isEqualTo(true);
            assertThat(year.get("effective_from").toString()).isEqualTo("2027-01-05");
            assertThat(jdbc.queryForObject("select topic_key from configuration_version where scope = 'YEAR_TERMS'", String.class)).isEqualTo("YEAR_TERMS:P" + programId + ":T" + termId);
            assertThat((String) year.get("v")).contains("\"recurringFeeInvoicesCount\": 10", "\"enrollmentFeeCount\": 2", "\"firstEnrollmentFeeMonth\": 2",
                    "\"secondEnrollmentFeeMonth\": 7", "\"enrollmentFeeLeadMonths\": 1", "\"lateEnrollmentFeeDueDays\": 12", "\"recurringFeeDueDays\": 15",
                    "\"paysWhilePaused\": true", "\"blocksExamRegistration\": false", "\"requireEnrollmentFeeForFinalExamOnlyStudents\": true");

            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'COHORT_ENTRY'", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select cohort_id from configuration_version where scope = 'COHORT_ENTRY'", Long.class)).isEqualTo(cohortId);
            assertThat(jdbc.queryForObject("select (config_values->'discount'->>'fraction')::numeric from configuration_version where scope = 'COHORT_ENTRY'", BigDecimal.class)).isEqualByComparingTo("0.15");

            Map<String, Object> late = jdbc.queryForMap("select (config_values->'monthlyLateFeeRate'->>'fraction')::numeric as rate, (config_values->>'lateFeeCapMonths')::int as cap, "
                    + "(config_values->>'gracePeriodDays')::int as grace, (config_values->>'suspensionAfterDays')::int as susp, (config_values->>'lateFeeDebitNoteDueDays')::int as nd, status "
                    + "from configuration_version where scope = 'LATE_PAYMENT'");
            assertThat((BigDecimal) late.get("rate")).isEqualByComparingTo("0.05");
            assertThat(late.get("cap")).isEqualTo(12);
            assertThat(late.get("grace")).isEqualTo(5);
            assertThat(late.get("susp")).isEqualTo(30);
            assertThat(late.get("nd")).isEqualTo(10);
            assertThat(late.get("status")).isEqualTo("EFFECTIVE");

            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope = 'COURSE_TERMS' and course_id = ?", Long.class, courseId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select (config_values->>'monthlyInstallmentDueDays')::int from configuration_version where scope = 'COURSE_TERMS'", Integer.class)).isEqualTo(10);
            assertThat(jdbc.queryForObject("select config_values::text from configuration_version where scope = 'ISSUANCE'", String.class)).contains("REVIEW", "[1, 3, 6, 12, 24]");
            assertThat(jdbc.queryForObject("select count(*) from configuration_version where scope in ('ALLOCATION', 'IRREGULAR_TERMS', 'ANNUAL_UPFRONT')", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from configuration_version v where not exists (select 1 from configuration_version_status_change c where c.configuration_version_id = v.id)", Long.class)).isZero();
        }
    }
}
