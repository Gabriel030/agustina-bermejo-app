package com.red_escuela.backend.identity;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class StudentGuardianColumnsMigrationTest {

    private static final String[] GUARDIAN_COLUMNS = {
        "guardian_full_name", "guardian_dni", "guardian_relationship", "guardian_phone", "guardian_email",
        "emergency_contact_name", "emergency_contact_relationship", "emergency_contact_phone"
    };

    @Test
    void existingStudentsKeepTheirRowsWithNullGuardianData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("183")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table student disable trigger all");
            jdbc.update("insert into student (person_id, institution_id) values (1, 1)");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from student", Integer.class)).isEqualTo(1);
            for (String column : GUARDIAN_COLUMNS) {
                assertThat(jdbc.queryForObject("select count(" + column + ") from student", Integer.class))
                        .as(column).isZero();
            }
        }
    }

    @Test
    void guardianColumnsAcceptTheMaximumLengthsOfTheRequests() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("183")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table student disable trigger all");
            jdbc.update("insert into student (person_id, institution_id) values (1, 1)");
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            jdbc.update("update student set guardian_full_name = ?, guardian_dni = ?, guardian_relationship = ?, "
                    + "guardian_phone = ?, guardian_email = ?, emergency_contact_name = ?, "
                    + "emergency_contact_relationship = ?, emergency_contact_phone = ?",
                    "n".repeat(200), "1".repeat(20), "r".repeat(60), "9".repeat(40), "e".repeat(255),
                    "n".repeat(200), "r".repeat(60), "9".repeat(40));

            assertThat(jdbc.queryForObject("select length(guardian_full_name) from student", Integer.class)).isEqualTo(200);
        }
    }
}
