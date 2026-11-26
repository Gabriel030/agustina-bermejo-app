package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.testsupport.MigrationTestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("migration")
class SectionEnrollmentStudentMatchesParentMigrationTest {

    private static final List<String> PARENT_TABLES = List.of("program_enrollment", "course_enrollment");
    private static final List<String> CHILD_TABLES = List.of("subject_section_enrollment", "course_section_enrollment");

    @Test
    void subjectSectionEnrollmentOfAnotherStudentIsAcceptedBeforeV478() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("475")) {
            JdbcTemplate jdbc = isolated(new JdbcTemplate(dataSource));
            insertProgramEnrollment(jdbc, 1, 1);

            assertThatCode(() -> insertSubjectSectionEnrollment(jdbc, 1, 2, 1)).doesNotThrowAnyException();
        }
    }

    @Test
    void courseSectionEnrollmentOfAnotherStudentIsAcceptedBeforeV478() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("475")) {
            JdbcTemplate jdbc = isolated(new JdbcTemplate(dataSource));
            insertCourseEnrollment(jdbc, 1, 1);

            assertThatCode(() -> insertCourseSectionEnrollment(jdbc, 1, 2, 1)).doesNotThrowAnyException();
        }
    }

    @Test
    void subjectSectionEnrollmentOfAnotherStudentThanItsProgramEnrollmentIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("478")) {
            JdbcTemplate jdbc = isolated(new JdbcTemplate(dataSource));
            insertProgramEnrollment(jdbc, 1, 1);

            assertThatThrownBy(() -> insertSubjectSectionEnrollment(jdbc, 1, 2, 1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_subject_section_enrollment_parent_student");
        }
    }

    @Test
    void courseSectionEnrollmentOfAnotherStudentThanItsCourseEnrollmentIsRejected() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("478")) {
            JdbcTemplate jdbc = isolated(new JdbcTemplate(dataSource));
            insertCourseEnrollment(jdbc, 1, 1);

            assertThatThrownBy(() -> insertCourseSectionEnrollment(jdbc, 1, 2, 1))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_course_section_enrollment_parent_student");
        }
    }

    @Test
    void sectionEnrollmentsOfTheParentStudentAreAccepted() {
        try (MigrationTestDatabase dataSource = MigrationTestDatabase.migratedTo("478")) {
            JdbcTemplate jdbc = isolated(new JdbcTemplate(dataSource));
            insertProgramEnrollment(jdbc, 1, 1);
            insertCourseEnrollment(jdbc, 1, 1);

            assertThatCode(() -> {
                insertSubjectSectionEnrollment(jdbc, 1, 1, 1);
                insertCourseSectionEnrollment(jdbc, 1, 1, 1);
            }).doesNotThrowAnyException();
        }
    }

    private JdbcTemplate isolated(JdbcTemplate jdbc) {
        for (String table : CHILD_TABLES) {
            dropForeignKeysExcept(jdbc, table, "_parent_student");
        }
        for (String table : PARENT_TABLES) {
            dropForeignKeysExcept(jdbc, table, "");
        }
        return jdbc;
    }

    private void dropForeignKeysExcept(JdbcTemplate jdbc, String table, String keptSuffix) {
        List<String> names = jdbc.queryForList(
                "select conname from pg_constraint where conrelid = ?::regclass and contype = 'f'", String.class, table);
        for (String name : names) {
            if (keptSuffix.isEmpty() || !name.endsWith(keptSuffix)) {
                jdbc.execute("alter table " + table + " drop constraint \"" + name + "\"");
            }
        }
    }

    private void insertProgramEnrollment(JdbcTemplate jdbc, long studentId, long programId) {
        jdbc.execute("alter table program_enrollment disable trigger all");
        jdbc.update("insert into program_enrollment (student_id, program_id, status, enrollment_date) values (?, ?, 'ENROLLED', now())",
                studentId, programId);
        jdbc.execute("alter table program_enrollment enable trigger all");
    }

    private void insertCourseEnrollment(JdbcTemplate jdbc, long studentId, long courseId) {
        jdbc.execute("alter table course_enrollment disable trigger all");
        jdbc.update("insert into course_enrollment (student_id, course_id, status, enrollment_date) values (?, ?, 'ENROLLED', now())",
                studentId, courseId);
        jdbc.execute("alter table course_enrollment enable trigger all");
    }

    private void insertSubjectSectionEnrollment(JdbcTemplate jdbc, long programEnrollmentId, long studentId, long sectionId) {
        jdbc.update("insert into subject_section_enrollment (program_enrollment_id, student_id, program_section_id, status, attempt_number, enrolled_at) values (?, ?, ?, 'ENROLLED', 1, now())",
                programEnrollmentId, studentId, sectionId);
    }

    private void insertCourseSectionEnrollment(JdbcTemplate jdbc, long courseEnrollmentId, long studentId, long sectionId) {
        jdbc.update("insert into course_section_enrollment (course_enrollment_id, student_id, course_section_id, status, attempt_number, enrolled_at) values (?, ?, ?, 'ENROLLED', 1, now())",
                courseEnrollmentId, studentId, sectionId);
    }
}
