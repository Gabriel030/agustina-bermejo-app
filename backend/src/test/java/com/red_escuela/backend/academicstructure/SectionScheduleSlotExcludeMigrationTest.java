package com.red_escuela.backend.academicstructure;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class SectionScheduleSlotExcludeMigrationTest {

    private static final String EXCLUSION_VIOLATION = "23P01";

    @Test
    void overlappingClassroomSlotsOfOccupyingSectionsAreRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
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
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
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
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
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
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
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
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
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

    @Test
    void promotingASectionWithOverlappingSlotToActiveIsRejectedForClassroomAndInstructor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.inactiveSection, f.classroom, "MONDAY", "09:00", "11:00");

            assertThatThrownBy(() -> jdbc.update("update subject_section set status = 'ACTIVE' where id = ?", f.inactiveSection))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));

            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.inactiveSection, instructor);
            jdbc.update("update section_schedule_slot set classroom_id = ? where subject_section_id = ?", f.otherClassroom, f.inactiveSection);

            assertThatThrownBy(() -> jdbc.update("update subject_section set status = 'ACTIVE' where id = ?", f.inactiveSection))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
        }
    }

    @Test
    void overlappingDraftSlotsAgainstActiveOrAgainstEachOtherAreAccepted() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long otherDraft = Fixture.section(jdbc, jdbc.queryForObject("select max(id) from subject", Long.class),
                    jdbc.queryForObject("select max(id) from term", Long.class), "D", "INACTIVE");
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.inactiveSection, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, otherDraft, f.classroom, "MONDAY", "08:00", "10:00");

            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    void courseSectionsAreExcludedForClassroomAndInstructor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long courseSubject = insertCourseSubject(jdbc, f.termId);
            Long courseA = insertCourseSection(jdbc, courseSubject, f.termId, "CA", "ACTIVE");
            Long courseB = insertCourseSection(jdbc, courseSubject, f.termId, "CB", "ACTIVE");
            Long instructor = insertInstructor(jdbc);
            jdbc.update("insert into course_section_instructor (course_section_id, instructor_id, role) values (?, ?, 'LEAD')", courseA, instructor);
            jdbc.update("insert into course_section_instructor (course_section_id, instructor_id, role) values (?, ?, 'LEAD')", courseB, instructor);
            insertCourseSlot(jdbc, courseA, f.classroom, "MONDAY", "08:00", "10:00");

            assertThatThrownBy(() -> insertCourseSlot(jdbc, courseB, f.classroom, "MONDAY", "09:00", "11:00"))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
            assertThatThrownBy(() -> insertCourseSlot(jdbc, courseB, f.otherClassroom, "MONDAY", "09:00", "11:00"))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
            insertCourseSlot(jdbc, courseB, f.otherClassroom, "MONDAY", "10:00", "12:00");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void deactivatingAnActiveSectionFreesItsClassroomAndInstructor() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.sectionB, instructor);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(1);

            jdbc.update("update subject_section set status = 'INACTIVE' where id = ?", f.sectionA);

            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isZero();
            insertSlot(jdbc, f.sectionB, f.classroom, "MONDAY", "09:00", "11:00");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void deletingSlotsLinksAndSectionsRemovesTheirProjectionRows() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.sectionB, instructor);
            Long slotA = insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "TUESDAY", "08:00", "10:00");
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(2);

            jdbc.update("delete from section_schedule_slot where id = ?", slotA);
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isEqualTo(1);

            jdbc.update("delete from subject_section_instructor where program_section_id = ?", f.sectionB);
            assertThat(jdbc.queryForObject("select count(*) from section_schedule_slot_instructor", Integer.class)).isZero();

            jdbc.update("delete from section_schedule_slot where subject_section_id = ?", f.sectionB);
            jdbc.update("delete from subject_section where id = ?", f.sectionB);
            assertThat(jdbc.queryForObject("select count(*) from subject_section where id = ?", Integer.class, f.sectionB)).isZero();
        }
    }

    @Test
    void termPeriodsDecideWhetherTheSameClassroomAndHourCollide() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("470")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long subject = jdbc.queryForObject("select max(id) from subject", Long.class);
            Long disjointTerm = insertTerm(jdbc, 2027, "2027-03-01", "2027-12-15");
            Long partialTerm = insertTerm(jdbc, 2028, "2026-12-01", "2027-02-28");
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");

            insertSlot(jdbc, Fixture.section(jdbc, subject, disjointTerm, "T1", "ACTIVE"), f.classroom, "MONDAY", "08:00", "10:00");
            Long partialSection = Fixture.section(jdbc, subject, partialTerm, "T2", "ACTIVE");
            assertThatThrownBy(() -> insertSlot(jdbc, partialSection, f.classroom, "MONDAY", "08:00", "10:00"))
                    .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
        }
    }

    @Test
    void preMigrationCheckReportsActiveOverlapsOfClassroomAndInstructorBeforeV470() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("469")) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture f = Fixture.create(jdbc);
            Long instructor = insertInstructor(jdbc);
            linkInstructor(jdbc, f.sectionA, instructor);
            linkInstructor(jdbc, f.sectionB, instructor);
            linkInstructor(jdbc, f.inactiveSection, instructor);
            insertSlot(jdbc, f.sectionA, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.inactiveSection, f.classroom, "MONDAY", "08:00", "10:00");
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "TUESDAY", "08:00", "10:00");

            List<String> queries = preMigrationQueries();
            assertThat(queries).hasSize(2);
            assertThat(jdbc.queryForList(queries.get(0))).isEmpty();
            assertThat(jdbc.queryForList(queries.get(1))).isEmpty();

            insertSlot(jdbc, f.sectionB, f.classroom, "MONDAY", "09:00", "11:00");
            insertSlot(jdbc, f.sectionB, f.otherClassroom, "MONDAY", "09:00", "11:00");

            List<Map<String, Object>> classroomOverlaps = jdbc.queryForList(queries.get(0));
            List<Map<String, Object>> instructorOverlaps = jdbc.queryForList(queries.get(1));
            System.out.println("[V470 pre-check] classroom overlaps: " + classroomOverlaps);
            System.out.println("[V470 pre-check] instructor overlaps: " + instructorOverlaps);
            assertThat(classroomOverlaps).hasSize(1);
            assertThat(instructorOverlaps).hasSize(3);
        }
    }

    private static List<String> preMigrationQueries() {
        try {
            List<String> lines = Files.readAllLines(Path.of("../docs/release/pre-migration-checks.sql"));
            int start = 0;
            while (!lines.get(start).contains("[V470] Franjas de aula")) {
                start++;
            }
            StringBuilder body = new StringBuilder();
            for (int i = start; i < lines.size() && !lines.get(i).trim().equals("rollback;"); i++) {
                if (!lines.get(i).startsWith("\\echo")) {
                    body.append(lines.get(i)).append('\n');
                }
            }
            return Arrays.stream(body.toString().split(";")).map(String::trim).filter(q -> !q.isEmpty()).toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Long insertTerm(JdbcTemplate jdbc, int year, String start, String end) {
        return jdbc.queryForObject("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) "
                + "values (?, 'ACTIVE', ?::timestamp, ?::timestamp, '2026-02-01', '2026-03-01') returning id", Long.class, year, start, end);
    }

    private static Long insertCourseSubject(JdbcTemplate jdbc, Long termId) {
        Long courseId = jdbc.queryForObject("insert into course (name, term_id, currency) values ('Curso', ?, 'ARS') returning id", Long.class, termId);
        return jdbc.queryForObject("insert into course_subject (name, course_id) values ('Modulo', ?) returning id", Long.class, courseId);
    }

    private static Long insertCourseSection(JdbcTemplate jdbc, Long courseSubjectId, Long termId, String name, String status) {
        return jdbc.queryForObject("insert into course_section (course_subject_id, term_id, name, status) values (?, ?, ?, ?) returning id",
                Long.class, courseSubjectId, termId, name, status);
    }

    private static Long insertCourseSlot(JdbcTemplate jdbc, Long sectionId, Long classroomId, String day, String start, String end) {
        return jdbc.queryForObject("insert into section_schedule_slot (course_section_id, classroom_id, day_of_week, start_time, end_time) "
                + "values (?, ?, ?, ?::time, ?::time) returning id", Long.class, sectionId, classroomId, day, start, end);
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

    private record Fixture(Long termId, Long classroom, Long otherClassroom, Long sectionA, Long sectionB, Long inactiveSection) {

        static Fixture create(JdbcTemplate jdbc) {
            jdbc.update("insert into term (year_number, status, start_date, end_date, enrollment_start, enrollment_end) values (2026, 'ACTIVE', '2026-03-01', '2026-12-15', '2026-02-01', '2026-03-01')");
            Long termId = jdbc.queryForObject("select max(id) from term", Long.class);
            Long classroom = jdbc.queryForObject("insert into classroom (name, capacity) values ('Aula 1', 20) returning id", Long.class);
            Long otherClassroom = jdbc.queryForObject("insert into classroom (name, capacity) values ('Aula 2', 20) returning id", Long.class);
            Long subject = jdbc.queryForObject("insert into subject (name, code) values ('Materia', 'MS-1') returning id", Long.class);
            return new Fixture(termId, classroom, otherClassroom,
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
