package com.red_escuela.backend.identity;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class InstitutionLegacyColumnsDropMigrationTest {

    private static final List<String> DROPPED_COLUMNS = List.of(
            "facebook", "tweeter", "pref_notas", "pref_calendario", "pref_asignar_curso",
            "sms_url", "sms_usuario", "sms_password");

    private static final List<String> KEPT_COLUMNS = List.of(
            "id", "name", "address_id", "phone_number", "secondary_phone_number", "website", "email",
            "justification_deadline_business_days", "evaluation_config_due_days", "announcement_email_enabled");

    @Test
    void legacyColumnsAreDroppedAndTheRestOfTheTableSurvivesWithItsData() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("213")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("""
                    insert into institution (name, phone_number, secondary_phone_number, website, email,
                                             facebook, tweeter, pref_notas, sms_url, sms_usuario, sms_password)
                    values ('Instituto legado', '111', '222', 'https://example.org', 'info@example.org',
                            'fb', 'tw', true, 'https://sms.example.org', 'user', 'secret')""");
            assertThat(columnsOf(jdbc)).containsAll(DROPPED_COLUMNS);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(columnsOf(jdbc)).doesNotContainAnyElementsOf(DROPPED_COLUMNS).containsAll(KEPT_COLUMNS);
            assertThat(jdbc.queryForMap("select name, phone_number, secondary_phone_number, website, email from institution"))
                    .containsEntry("name", "Instituto legado")
                    .containsEntry("phone_number", "111")
                    .containsEntry("secondary_phone_number", "222")
                    .containsEntry("website", "https://example.org")
                    .containsEntry("email", "info@example.org");
        }
    }

    private List<String> columnsOf(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = current_schema() and table_name = 'institution'""", String.class);
    }
}
