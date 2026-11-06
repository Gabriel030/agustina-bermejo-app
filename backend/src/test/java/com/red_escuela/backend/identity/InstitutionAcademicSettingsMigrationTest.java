package com.red_escuela.backend.identity;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class InstitutionAcademicSettingsMigrationTest {

    @Test
    void theExistingInstitutionRowGetsTheDefaultSettings() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("185")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into institution (name) values ('Instituto existente')");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from institution", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select justification_deadline_business_days from institution", Integer.class))
                    .isEqualTo(5);
            assertThat(jdbc.queryForObject("select evaluation_config_due_days from institution", Integer.class))
                    .isEqualTo(15);
        }
    }

    @Test
    void theChecksRejectValuesOutsideTheAllowedRanges() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("185")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into institution (name) values ('Instituto existente')");
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThatThrownBy(() -> jdbc.update("update institution set justification_deadline_business_days = 61"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update institution set evaluation_config_due_days = 0"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("update institution set justification_deadline_business_days = 0, evaluation_config_due_days = 120");
        }
    }
}
