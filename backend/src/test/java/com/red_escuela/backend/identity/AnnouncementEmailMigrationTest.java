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
class AnnouncementEmailMigrationTest {

    private static final String INSERT_DELIVERY = """
            insert into notification_delivery (dedup_key, channel, category, kind, priority, recipient_person_id, status, scheduled_for, body_text, subject, created_at)
            values (?, 'EMAIL', ?, 'KIND', 1, ?, 'QUEUED', current_date, 'texto', 'asunto', now())""";

    @Test
    void existingRowsSurviveTheAnnouncementCategoryAndTheSwitchDefaultsToOff() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("186")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into institution (name) values ('Instituto existente')");
            Long personId = insertPerson(jdbc);
            jdbc.update(INSERT_DELIVERY, "collection:1", "COLLECTION_REMINDER", personId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select count(*) from notification_delivery where category = 'COLLECTION_REMINDER'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select announcement_email_enabled from institution", Boolean.class)).isFalse();
            jdbc.update(INSERT_DELIVERY, "announcement:1", "ANNOUNCEMENT", personId);
            assertThatThrownBy(() -> jdbc.update(INSERT_DELIVERY, "other:1", "UNKNOWN", personId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private Long insertPerson(JdbcTemplate jdbc) {
        jdbc.update("insert into address default values");
        Long addressId = jdbc.queryForObject("select max(id) from address", Long.class);
        jdbc.update("insert into person (name, last_name, dni, email, username, address_id) values ('Seed', 'Person', '30111222', 'seed@example.com', 'seed.person', ?)", addressId);
        return jdbc.queryForObject("select max(id) from person", Long.class);
    }
}
