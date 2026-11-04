package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class SectionShiftAndCodeMigrationTest {

    private static void insertSectionWithSlot(JdbcTemplate jdbc, String name, Long subjectId, Long termId, String start, String end) {
        jdbc.update("insert into subject_section (subject_id, term_id, name) values (?, ?, ?)", subjectId, termId, name);
        if (start != null) {
            Long sectionId = jdbc.queryForObject("select max(id) from subject_section", Long.class);
            jdbc.update("insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) "
                    + "values (?, 1, 'MONDAY', ?::time, ?::time)", sectionId, start, end);
        }
    }

    private static String shiftOf(JdbcTemplate jdbc, String name) {
        return jdbc.queryForObject("select shift from subject_section where name = ?", String.class, name);
    }

    @Test
    void existingSectionsGetShiftFromTheirFirstSlotAndAUniqueCode() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("178")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("alter table subject_section disable trigger all");
            jdbc.execute("alter table course_section disable trigger all");
            jdbc.execute("alter table section_schedule_slot disable trigger all");
            jdbc.update("insert into subject (name, code) values ('Matematica 1', 'MAT1')");
            Long subjectId = jdbc.queryForObject("select max(id) from subject", Long.class);
            jdbc.update("insert into term (year_number, type, status, start_date, end_date, enrollment_start, enrollment_end) "
                    + "values (2026, 'FIRST_SEMESTER', true, '2026-03-01', '2026-07-31', '2026-02-01', '2026-03-10')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            insertSectionWithSlot(jdbc, "Sin franjas", subjectId, termId, null, null);
            insertSectionWithSlot(jdbc, "Manana", subjectId, termId, "09:00", "11:00");
            insertSectionWithSlot(jdbc, "Tarde", subjectId, termId, "14:00", "16:00");
            insertSectionWithSlot(jdbc, "Noche", subjectId, termId, "19:00", "21:00");
            jdbc.update("insert into course_section (course_subject_id, term_id, name) values (7, ?, 'Comision curso')", termId);

            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

            assertThat(shiftOf(jdbc, "Sin franjas")).isEqualTo("MORNING");
            assertThat(shiftOf(jdbc, "Manana")).isEqualTo("MORNING");
            assertThat(shiftOf(jdbc, "Tarde")).isEqualTo("AFTERNOON");
            assertThat(shiftOf(jdbc, "Noche")).isEqualTo("EVENING");
            assertThat(jdbc.queryForList("select code from subject_section order by id", String.class))
                    .containsExactly("MAT1-2026C1-01", "MAT1-2026C1-02", "MAT1-2026C1-03", "MAT1-2026C1-04");
            assertThat(jdbc.queryForObject("select code from course_section", String.class)).isEqualTo("CM7-2026C1-01");
            assertThat(jdbc.queryForObject(
                    "select last_value from section_code_counter where prefix = 'MAT1' and period = '2026C1'", Integer.class))
                    .isEqualTo(4);
            assertThat(jdbc.queryForObject("select name from subject_section where code = 'MAT1-2026C1-03'", String.class))
                    .isEqualTo("Tarde");
            assertThatThrownBy(() -> jdbc.update("update subject_section set code = 'MAT1-2026C1-01' where name = 'Noche'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void defaultShiftRangesAreSeeded() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("179")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            assertThat(jdbc.queryForList("select shift || ' ' || start_time || '-' || end_time from shift_range order by start_time", String.class))
                    .containsExactly("MORNING 08:00:00-12:30:00", "AFTERNOON 13:30:00-18:00:00", "EVENING 18:30:00-22:30:00");
            assertThatThrownBy(() -> jdbc.update("update shift_range set end_time = start_time where shift = 'MORNING'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
