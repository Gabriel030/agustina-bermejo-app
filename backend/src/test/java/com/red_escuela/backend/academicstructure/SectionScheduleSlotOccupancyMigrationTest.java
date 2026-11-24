package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("migration")
class SectionScheduleSlotOccupancyMigrationTest {

    @Test
    void existingSlotsAreBackfilledWithOccupancyTermPeriodAndTimeSpan() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("447")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) values (2026, 'ACTIVE', '2026-03-01', '2026-12-15', '2026-02-01', '2026-03-01')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            jdbc.update("insert into classroom (name, capacity) values ('Aula sembrada', 20)");
            Long classroomId = jdbc.queryForObject("select max(id) from classroom", Long.class);
            jdbc.update("insert into subject (name, code) values ('Materia sembrada', 'MS-9')");
            Long subjectId = jdbc.queryForObject("select max(id) from subject", Long.class);
            Long activeSectionId = insertSection(jdbc, subjectId, termId, "Activa", "ACTIVE");
            Long inactiveSectionId = insertSection(jdbc, subjectId, termId, "Inactiva", "INACTIVE");
            Long activeSlotId = insertSlot(jdbc, activeSectionId, classroomId, "MONDAY");
            Long inactiveSlotId = insertSlot(jdbc, inactiveSectionId, classroomId, "TUESDAY");

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(jdbc.queryForObject("select occupies from section_schedule_slot where id = ?", Boolean.class, activeSlotId)).isTrue();
            assertThat(jdbc.queryForObject("select occupies from section_schedule_slot where id = ?", Boolean.class, inactiveSlotId)).isFalse();
            assertThat(jdbc.queryForObject("select time_span::text from section_schedule_slot where id = ?", String.class, activeSlotId))
                    .isEqualTo("[28800,36000)");
            assertThat(jdbc.queryForObject("select time_span::text from section_schedule_slot where id = ?", String.class, inactiveSlotId))
                    .isEqualTo("[115200,122400)");
            assertThat(jdbc.queryForObject("select term_period::text from section_schedule_slot where id = ?", String.class, activeSlotId))
                    .isEqualTo("[\"2026-03-01 00:00:00\",\"2026-12-15 00:00:00\"]");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot", Integer.class)).isEqualTo(2);
        }
    }

    private Long insertSection(JdbcTemplate jdbc, Long subjectId, Long termId, String name, String status) {
        return jdbc.queryForObject("insert into subject_section (subject_id, term_id, name, status) values (?, ?, ?, ?) returning id",
                Long.class, subjectId, termId, name, status);
    }

    private Long insertSlot(JdbcTemplate jdbc, Long sectionId, Long classroomId, String day) {
        return jdbc.queryForObject("insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) "
                + "values (?, ?, ?, '08:00', '10:00') returning id", Long.class, sectionId, classroomId, day);
    }
}
