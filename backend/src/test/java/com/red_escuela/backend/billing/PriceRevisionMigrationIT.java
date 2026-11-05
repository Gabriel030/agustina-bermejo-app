package com.red_escuela.backend.billing;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.billing.pricing.repository.PriceVersionRepository;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-214")
@Tag("D-036")
@Tag("D-035")
@Tag("D-240")
@Tag("D-097")
@Tag("migration")
class PriceRevisionMigrationIT {

    private record Seed(JdbcTemplate jdbc, long personId, long accountId, long invoiceId, long programId, long courseId) {
    }

    private static void migrateTo(DriverManagerDataSource dataSource, String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private static Seed seedBeforeTheMigration(DriverManagerDataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
        Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values ('seed', 'seed@example.com', ?, 'ACTIVE') returning id", Long.class, addressId);
        Long institutionId = jdbc.queryForObject("insert into institution (name) values ('Instituto') returning id", Long.class);
        Long studentId = jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id", Long.class, personId, institutionId);
        Long accountId = jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """, Long.class, studentId);
        Long invoiceId = jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status, payment_status, invoice_number,
                    issue_date, due_date, student_snapshot, lines_total, total, outstanding_amount, discounts_total, paid_amount, credited_amount,
                    lines_total_currency, discounts_total_currency, total_currency, paid_amount_currency, credited_amount_currency, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'ISSUED', 'PENDING', 'F-00000001', date '2025-07-01', date '2025-07-11', cast('{"fullName":"Ana"}' as jsonb),
                    100, 100, 100, 0, 0, 0, 'ARS', 'ARS', 'ARS', 'ARS', 'ARS', 'ARS', now(), now()) returning id
                """, Long.class, accountId);
        Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values ('Enfermería', true, 3, 'ARS') returning id", Long.class);
        jdbc.update("insert into term (year_number, status, start_date, end_date) values (2025, true, timestamp '2025-03-01 00:00:00', timestamp '2025-12-15 00:00:00')");
        Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
        Long courseId = jdbc.queryForObject("insert into course (name, term_id, currency) values ('Primeros Auxilios', ?, 'ARS') returning id", Long.class, termId);
        return new Seed(jdbc, personId, accountId, invoiceId, programId, courseId);
    }

    private static long insertRevision(Seed seed, Long programId, Long courseId, String feeType, String amount, String currency, String effectiveFrom, String createdAt) {
        return seed.jdbc().queryForObject("""
                insert into price_revision (program_id, course_id, fee_type, description, monthly_amount, monthly_amount_currency, effective_from, created_by_id, created_at)
                values (?, ?, ?, 'Descripcion ' || ?, cast(? as numeric), ?, cast(? as date), ?, cast(? as timestamp)) returning id
                """, Long.class, programId, courseId, feeType, effectiveFrom, amount, currency, effectiveFrom, seed.personId(), createdAt);
    }

    private static long insertLine(Seed seed, int number, String feeType, String amount, long priceRevisionId) {
        return seed.jdbc().queryForObject("""
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount, discount_amount_currency,
                    credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency, price_revision_id)
                values (?, 'ARS', ?, ?, 'Concepto', date '2025-07-01', ?, cast(? as numeric), 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS', ?) returning id
                """, Long.class, seed.invoiceId(), number, feeType, "key-" + number, amount, priceRevisionId);
    }

    @Test
    void historicalRevisionsBecomeVersionsOfOnePricePerOfferingWithoutRewritingAnyIssuedLine() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("102")) {
            Seed seed = seedBeforeTheMigration(dataSource);
            long oldest = insertRevision(seed, seed.programId(), null, "MONTHLY_INSTALLMENT", "100000.00", "ARS", "2020-01-01", "2020-01-01 09:00:00");
            long sameDayCorrection = insertRevision(seed, seed.programId(), null, "MONTHLY_INSTALLMENT", "110000.00", "ARS", "2020-01-01", "2020-01-01 18:00:00");
            long newest = insertRevision(seed, seed.programId(), null, "MONTHLY_INSTALLMENT", "120000.00", "ARS", "2025-06-01", "2025-06-01 09:00:00");
            long enrollmentFee = insertRevision(seed, seed.programId(), null, "ENROLLMENT_FEE", "50000.00", "ARS", "2020-01-01", "2020-01-01 09:00:00");
            long coursePrice = insertRevision(seed, null, seed.courseId(), "MONTHLY_INSTALLMENT", "15000.50", "ARS", "2025-02-01", "2025-02-01 09:00:00");
            insertLine(seed, 1, "MONTHLY_INSTALLMENT", "100000.00", oldest);
            insertLine(seed, 2, "MONTHLY_INSTALLMENT", "120000.00", newest);
            insertLine(seed, 3, "ENROLLMENT_FEE", "50000.00", enrollmentFee);
            insertLine(seed, 4, "MONTHLY_INSTALLMENT", "15000.50", coursePrice);
            List<Map<String, Object>> linesBefore = seed.jdbc().queryForList("select * from invoice_line order by id");
            BigDecimal totalBefore = seed.jdbc().queryForObject("select sum(amount) from invoice_line", BigDecimal.class);

            migrateTo(dataSource, "105");

            JdbcTemplate jdbc = seed.jdbc();
            List<Map<String, Object>> linesAfter = jdbc.queryForList("select * from invoice_line order by id").stream().map(PriceRevisionMigrationIT::asBeforeTheMigration).toList();
            assertThat(linesAfter).hasSize(4).isEqualTo(linesBefore);
            assertThat(jdbc.queryForObject("select sum(amount) from invoice_line", BigDecimal.class)).isEqualByComparingTo(totalBefore);
            assertThat(jdbc.queryForObject("select count(*) from invoice_line where marketing_discount_amount = 0 and list_amount = amount", Long.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("select count(*) from price", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select description from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", String.class, seed.programId())).isEqualTo("Descripcion 2025-06-01");
            assertThat(statusOf(jdbc, newest)).isEqualTo("EFFECTIVE");
            assertThat(statusOf(jdbc, sameDayCorrection)).isEqualTo("SUPERSEDED");
            assertThat(statusOf(jdbc, oldest)).isEqualTo("SUPERSEDED");
            assertThat(statusOf(jdbc, enrollmentFee)).isEqualTo("EFFECTIVE");
            assertThat(statusOf(jdbc, coursePrice)).isEqualTo("EFFECTIVE");
            assertThat(jdbc.queryForList("select id from price_version where initial_version order by id", Long.class)).containsExactlyInAnyOrder(oldest, enrollmentFee, coursePrice);
            assertThat(jdbc.queryForObject("select count(*) from price_version where marketing_discount_type is not null or marketing_discount_percentage is not null or marketing_discount_amount is not null or marketing_discount_amount_currency is not null", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from price_version where amount_currency <> 'ARS' or not direct_publication", Long.class)).isZero();
            PriceReader priceReader = priceReaderOver(dataSource);
            OfferingReference program = OfferingReference.ofProgram(seed.programId());
            PriceVersionResolution sameDay = priceReader.requirePriceVersionEffectiveOn(program, FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2020, 1, 1));
            assertThat(sameDay.priceVersionId()).isEqualTo(sameDayCorrection);
            assertThat(sameDay.finalAmount().value().amount()).isEqualByComparingTo("110000.00");
            assertThat(sameDay.marketingDiscountAmount().value().isZero()).isTrue();
            assertThat(priceReader.requirePriceVersionEffectiveOn(program, FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2025, 6, 1)).priceVersionId()).isEqualTo(newest);
            assertThat(priceReader.requirePriceVersionEffectiveOn(program, FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2024, 12, 31)).priceVersionId()).isEqualTo(sameDayCorrection);
            assertThat(priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofCourse(seed.courseId()), FeeType.MONTHLY_INSTALLMENT, LocalDate.of(2025, 2, 1)).priceVersionId())
                    .isEqualTo(coursePrice);
            assertThat(jdbc.queryForObject("select count(*) from price_version v where not exists (select 1 from price_version_status_change c where c.price_version_id = v.id)", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from price_version_status_change where to_status = 'SUPERSEDED'", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select change_reason from price_version where id = ?", String.class, oldest)).isEqualTo("Descripcion 2020-01-01");
            assertThat(jdbc.queryForObject("select change_reason from price_version where id = ?", String.class, newest)).isEqualTo("Descripcion 2025-06-01");
            assertThat(jdbc.queryForObject("select change_reason from price_version where id = ?", String.class, coursePrice)).isEqualTo("Descripcion 2025-02-01");
            assertThat(jdbc.queryForObject("select reason from price_version_status_change c where c.price_version_id = ? and c.to_status = 'EFFECTIVE'", String.class, newest))
                    .isEqualTo("Descripcion 2025-06-01");
        }
    }

    @Test
    void aRevisionInAnotherCurrencyThanItsOfferingStopsTheMigrationInsteadOfMigratingSilently() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("102")) {
            Seed seed = seedBeforeTheMigration(dataSource);
            insertRevision(seed, seed.programId(), null, "MONTHLY_INSTALLMENT", "300.00", "USD", "2025-06-01", "2025-06-01 09:00:00");

            assertThatThrownBy(() -> migrateTo(dataSource, "105"))
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("currency different from the currency of their offering");
            assertThat(seed.jdbc().queryForObject("select count(*) from price_revision", Long.class)).isEqualTo(1);
        }
    }

    private static Map<String, Object> asBeforeTheMigration(Map<String, Object> afterTheMigration) {
        Map<String, Object> row = new java.util.LinkedHashMap<>(afterTheMigration);
        row.remove("marketing_discount_amount");
        row.remove("list_amount");
        row.put("price_revision_id", row.remove("price_version_id"));
        return row;
    }

    private static PriceReader priceReaderOver(DriverManagerDataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.red_escuela.backend.billing.pricing.domain");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.physical_naming_strategy", CamelCaseToUnderscoresNamingStrategy.class.getName()));
        factory.afterPropertiesSet();
        EntityManager entityManager = factory.getObject().createEntityManager();
        return new PriceReader(new JpaRepositoryFactory(entityManager).getRepository(PriceVersionRepository.class));
    }

    private static String statusOf(JdbcTemplate jdbc, long versionId) {
        return jdbc.queryForObject("select status from price_version where id = ?", String.class, versionId);
    }
}
