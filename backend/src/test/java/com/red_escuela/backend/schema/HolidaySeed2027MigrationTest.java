package com.red_escuela.backend.schema;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import com.red_escuela.backend.testsupport.SlowTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Migrates a real database to the version before the 2027 holiday seed and then to the latest version")
@Tag("migration")
class HolidaySeed2027MigrationTest {

    private static final String VERSION_BEFORE_SEED = "484";

    @Test
    void seedLoadsSixteenHolidaysOf2027WithoutTouchingOtherYears() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(VERSION_BEFORE_SEED)) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Integer holidaysOf2026 = jdbc.queryForObject("select count(*) from holiday where date between '2026-01-01' and '2026-12-31'", Integer.class);

            migrateToLatest(dataSource);

            assertThat(jdbc.queryForObject("select count(*) from holiday where date between '2027-01-01' and '2027-12-31'", Integer.class)).isEqualTo(16);
            assertThat(jdbc.queryForObject("select count(*) from holiday where date between '2026-01-01' and '2026-12-31'", Integer.class)).isEqualTo(holidaysOf2026);
            assertThat(jdbc.queryForObject("select count(*) from holiday where date between '2027-01-01' and '2027-12-31' and type <> 'NATIONAL'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from holiday where date between '2027-01-01' and '2027-12-31' and name like '%(a confirmar traslado)'", Integer.class)).isEqualTo(4);
        }
    }

    @Test
    void seedKeepsHolidayAlreadyLoadedForTheSameDate() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo(VERSION_BEFORE_SEED)) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into holiday (date, description, name, type) values ('2027-01-01', 'Manual', 'Manual', 'OTHER')");

            migrateToLatest(dataSource);

            assertThat(jdbc.queryForObject("select count(*) from holiday where date between '2027-01-01' and '2027-12-31'", Integer.class)).isEqualTo(16);
            assertThat(jdbc.queryForObject("select name from holiday where date = '2027-01-01'", String.class)).isEqualTo("Manual");
        }
    }

    private static void migrateToLatest(MigrationTestDatabase dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
}
