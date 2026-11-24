package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class SectionScheduleSlotExcludeMigrationTest {

    private static final String EXCLUSION_VIOLATION = "23P01";

    @Test
    void overlappingClassroomSlotsOfOccupyingSectionsAreRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("464")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");

            assertThatThrownBy(() -> insertSlot(jdbc, f.sectionB, f.classroom, "MONDAY", "09:00", "11:00"))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e.getCause()).getSQLState()).isEqualTo(EXCLUSION_VIOLATION));
        }
    }

    @Test
    void adjacentOrDifferentDayOrInactiveClassroomSlotsAreAccepted() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("464")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");

            insertSlot(jdbc, f.sectionB, f.classroom, "MONDAY", "10:00", "12:00");
            insertSlot(jdbc, f.sectionB, f.classroom, "TUESDAY", "08:00", "10:00");
            insertSlot(jdbc, f.inactiveSection, f.classroom, "MONDAY", "08:00", "10:00");

            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot", Integer.class)).isEqualTo(4);
        }
    }

    @Test
    void overlappingInstructorSlotsInDifferentClassroomsAreRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("464")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.sectionB, instructor);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");

            assertThatThrownBy(() -> insertSlot(jdbc, f.sectionB, f.otherClassroom, "MONDAY", "09:00", "11:00"))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
        }
    }

    @Test
    void assigningInstructorToSectionWithOverlappingSlotIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("464")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "MONDAY", "09:00", "11:00");

            assertThatThrownBy(() -> linkInstructor(jdbc, f.sectionB, instructor))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
        }
    }

    @Test
    void nonOverlappingOrInactiveInstructorSlotsAreAcceptedAndRemovingLinkFreesTheInstructor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("464")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.sectionB, instructor);
            linkInstructor(jdbc, f.inactiveSection, instructor);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "MONDAY", "10:00", "12:00");
            insertSlot(jdbc, f.inactiveSection, f.otherClassroom, "MONDAY", "08:00", "10:00");

            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(2);

            jdbc.update("delete from subject_section_instructor where program_section_id = ?", f.sectionA);
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "MONDAY", "08:00", "10:00");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(2);
        }
    }

    private static String sqlState(Throwable e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof SQLException)) {
            cause = cause.getCause();
        }
        return cause == null ? null : ((SQLException) cause).getSQLState();
    }

    private static Long insertInstructor(JdbcTemplate jdbc) {
        jdbc.execute("alter table instructor alter column person_id drop not null");
        return jdbc.queryForObject("insert into instructor default values returning id", Long.class);
    }

    private static void linkInstructor(JdbcTemplate jdbc, Long sectionId, Long instructorId) {
        jdbc.update("insert into subject_section_instructor (program_section_id, instructor_id, role) values (?, ?, 'LEAD')",
                sectionId, instructorId);
    }

    private static Long insertSlot(JdbcTemplate jdbc, Long sectionId, Long classroomId, String day, String start, String end) {
        return jdbc.queryForObject("insert into section_schedule_slot (subject_section_id, classroom_id, day_of_week, start_time, end_time) "
                + "values (?, ?, ?, ?::time, ?::time) returning id", Long.class, sectionId, classroomId, day, start, end);
    }

    private record Fixture(Long classroom, Long otherClassroom, Long sectionA, Long sectionB, Long inactiveSection) {

        static Fixture create(JdbcTemplate jdbc) {
            jdbc.update("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) values (2026, 'ACTIVE', '2026-03-01', '2026-12-15', '2026-02-01', '2026-03-01')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            Long classroom = jdbc.queryForObject("insert into classroom (name, capacity) values ('Aula 1', 20) returning id", Long.class);
            Long otherClassroom = jdbc.queryForObject("insert into classroom (name, capacity) values ('Aula 2', 20) returning id", Long.class);
            Long subject = jdbc.queryForObject("insert into subject (name, code) values ('Materia', 'MS-1') returning id", Long.class);
            return new Fixture(classroom, otherClassroom,
                    section(jdbc, subject, termId, "A", "ACTIVE"),
                    section(jdbc, subject, termId, "B", "ACTIVE"),
                    section(jdbc, subject, termId, "C", "INACTIVE"));
        }

        private static Long section(JdbcTemplate jdbc, Long subjectId, Long termId, String name, String status) {
            return jdbc.queryForObject("insert into subject_section (subject_id, term_id, name, status) values (?, ?, ?, ?) returning id",
                    Long.class, subjectId, termId, name, status);
        }
    }
}
