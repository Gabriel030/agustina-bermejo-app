package com.red_escuela.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("D-273")
@Tag("D-536")
@Tag("migration")
class OfferingPriceBackfillMigrationIT {

    private static void migrateTo(DriverManagerDataSource dataSource, String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private static long programWith(JdbcTemplate jdbc, String name, String price, String enrollmentFee) {
        return jdbc.queryForObject("insert into program (name, status, total_years, currency, price, enrollment_fee_amount) values (?, 'ACTIVE', 3, 'ARS', cast(? as numeric), cast(? as numeric)) returning id",
                Long.class, name, price, enrollmentFee);
    }

    private static long priceOf(JdbcTemplate jdbc, long programId, String feeType) {
        return jdbc.queryForObject("insert into price (program_id, fee_type, currency, description) values (?, ?, 'ARS', 'Cuota') returning id", Long.class, programId, feeType);
    }

    private static long versionOf(JdbcTemplate jdbc, long priceId, String amount, String status, long personId) {
        return jdbc.queryForObject("""
                insert into price_version (price_id, amount, amount_currency, effective_from, status, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at, direct_publication, initial_version)
                values (?, cast(? as numeric), 'ARS', date '2027-01-01', ?, 'x', ?, now(), ?, now(), true, true) returning id
                """, Long.class, priceId, amount, status, personId, personId);
    }

    private static List<Map<String, Object>> versionsOf(JdbcTemplate jdbc, long programId, String feeType) {
        return jdbc.queryForList("select v.amount, v.status, v.initial_version from price_version v join price p on p.id = v.price_id where p.program_id = ? and p.fee_type = ? order by v.id",
                programId, feeType);
    }

    @Test
    void onlyOfferingsWithAValidColumnAndNoVersionReceiveAnEffectiveVersionAndRunningTheScriptAgainDoesNotDuplicate() throws IOException {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("221")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
            Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values ('seed', 'seed@example.com', ?, 'ACTIVE') returning id", Long.class, addressId);
            long withoutVersion = programWith(jdbc, "Sin version", "1000.00", "500.00");
            long divergent = programWith(jdbc, "Divergente", "999.00", null);
            versionOf(jdbc, priceOf(jdbc, divergent, "MONTHLY_INSTALLMENT"), "1500.00", "EFFECTIVE", personId);
            long proposed = programWith(jdbc, "Propuesta", "700.00", null);
            versionOf(jdbc, priceOf(jdbc, proposed, "MONTHLY_INSTALLMENT"), "700.00", "PROPOSED", personId);
            long invalid = programWith(jdbc, "Invalidos", "-5.00", "-1.00");
            long free = programWith(jdbc, "Sin precio", null, "0.00");
            jdbc.update("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) values (2025, 'ACTIVE', timestamp '2025-03-01 00:00:00', timestamp '2025-12-15 00:00:00', timestamp '2025-01-01 00:00:00', timestamp '2025-02-01 00:00:00')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            long course = jdbc.queryForObject("insert into course (name, term_id, currency, price) values ('Curso', ?, 'ARS', 800.00) returning id", Long.class, termId);

            migrateTo(dataSource, "222");

            assertThat(versionsOf(jdbc, withoutVersion, "MONTHLY_INSTALLMENT")).singleElement()
                    .satisfies(row -> {
                        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("1000.00");
                        assertThat(row.get("status")).isEqualTo("EFFECTIVE");
                        assertThat(row.get("initial_version")).isEqualTo(true);
                    });
            assertThat(versionsOf(jdbc, withoutVersion, "ENROLLMENT_FEE")).singleElement()
                    .satisfies(row -> assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("500.00"));
            assertThat(jdbc.queryForObject("select count(*) from price_version v join price p on p.id = v.price_id where p.course_id = ? and v.amount = 800.00 and v.status = 'EFFECTIVE'", Long.class, course)).isEqualTo(1L);
            assertThat(versionsOf(jdbc, divergent, "MONTHLY_INSTALLMENT")).singleElement()
                    .satisfies(row -> assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("1500.00"));
            assertThat(versionsOf(jdbc, proposed, "MONTHLY_INSTALLMENT")).singleElement()
                    .satisfies(row -> assertThat(row.get("status")).isEqualTo("PROPOSED"));
            assertThat(jdbc.queryForObject("select count(*) from price where program_id in (?, ?)", Long.class, invalid, free)).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from price_version_status_change c join price_version v on v.id = c.price_version_id join price p on p.id = v.price_id
                    where p.program_id = ? and c.to_status = 'EFFECTIVE' and c.event = 'PUBLISH_FOR_TODAY' and c.actor_process = 'OfferingPriceBackfill'
                    """, Long.class, withoutVersion)).isEqualTo(2L);
            long versionsBefore = jdbc.queryForObject("select count(*) from price_version", Long.class);
            long changesBefore = jdbc.queryForObject("select count(*) from price_version_status_change", Long.class);

            String script = Files.readString(Path.of("src/main/resources/db/migration/V222__offering_price_single_source_backfill.sql"), StandardCharsets.UTF_8);
            jdbc.execute(script);

            assertThat(jdbc.queryForObject("select count(*) from price_version", Long.class)).isEqualTo(versionsBefore);
            assertThat(jdbc.queryForObject("select count(*) from price_version_status_change", Long.class)).isEqualTo(changesBefore);
        }
    }
}
