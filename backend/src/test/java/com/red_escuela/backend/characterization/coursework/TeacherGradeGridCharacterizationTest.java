package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherGradeGridCharacterizationTest extends CourseworkCharacterizationSupport {

    private Classroom classroom(String key, String dniBase, EvaluationScheme scheme, Percentage maxAbsenceRate) {
        return persistClassroom(key, dniBase, scheme, maxAbsenceRate, 10, 10, InstructorRole.ASSISTANT);
    }

    private String gridPath(Classroom classroom) {
        return "/program-sections/" + classroom.section().getId() + "/grade-grid";
    }

    private void loadGrade(Classroom classroom, GradeItemType type, int sequence, String score) throws Exception {
        postJson(classroom.teacher(), "/section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(type, sequence, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal(score), "comentario", null))
                .andExpect(status().isCreated());
    }

    private void loadAttendance(Classroom classroom, int day, AttendanceStatus attendanceStatus) throws Exception {
        postJson(classroom.teacher(), "/program-sections/" + classroom.section().getId() + "/attendance",
                new CargarAsistenciaRequest(LocalDate.of(2026, 3, day),
                        List.of(new AttendanceEntry(classroom.enrollment().getId(), attendanceStatus)), null, null))
                .andExpect(status().isCreated());
    }

    @Test
    void gridListsColumnsOfTheSchemeAndTheNotesAlreadyLoadedForAnAssistantTeacher() throws Exception {
        Classroom classroom = classroom("grid1", "8100001", persistScheme(2, true, null), null);
        loadGrade(classroom, GradeItemType.EXAM, 1, "7");

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].type").value("EXAM"))
                .andExpect(jsonPath("$.columns[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$.columns[2].type").value("FINAL"))
                .andExpect(jsonPath("$.students.length()").value(1))
                .andExpect(jsonPath("$.students[0].enrollmentId").value(classroom.enrollment().getId()))
                .andExpect(jsonPath("$.students[0].dni").value("81000012"))
                .andExpect(jsonPath("$.students[0].condition").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.students[0].finalEnabled").value(false))
                .andExpect(jsonPath("$.students[0].grades.length()").value(1))
                .andExpect(jsonPath("$.students[0].grades[0].type").value("EXAM"))
                .andExpect(jsonPath("$.students[0].grades[0].score").value(7.0))
                .andExpect(jsonPath("$.students[0].grades[0].comments").value("comentario"));
    }

    @Test
    void studentWithoutNotesHasNoGradesAndIsInProgress() throws Exception {
        Classroom classroom = classroom("grid2", "8100002", persistScheme(2, true, null), null);

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[0].grades.length()").value(0))
                .andExpect(jsonPath("$.students[0].condition").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.students[0].finalScore").doesNotExist());
    }

    @Test
    void studentBecomesRegularEnablesTheFinalAndThenShowsTheApprovedFinalScore() throws Exception {
        Classroom classroom = classroom("grid3", "8100003", persistScheme(2, true, null), null);
        loadGrade(classroom, GradeItemType.EXAM, 1, "6");
        loadGrade(classroom, GradeItemType.EXAM, 2, "6");

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(jsonPath("$.students[0].condition").value("REGULAR"))
                .andExpect(jsonPath("$.students[0].finalEnabled").value(true));

        loadGrade(classroom, GradeItemType.FINAL, 1, "8");

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(jsonPath("$.students[0].condition").value("APPROVED"))
                .andExpect(jsonPath("$.students[0].finalScore").value(8.0))
                .andExpect(jsonPath("$.students[0].finalEnabled").value(true))
                .andExpect(jsonPath("$.students[0].grades.length()").value(3));
    }

    @Test
    void studentWhoFailedTheCourseworkIsFailedAndCannotSitTheFinal() throws Exception {
        Classroom classroom = classroom("grid4", "8100004", persistScheme(2, true, null), null);
        loadGrade(classroom, GradeItemType.EXAM, 1, "2");
        loadGrade(classroom, GradeItemType.EXAM, 2, "3");

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(jsonPath("$.students[0].condition").value("FAILED"))
                .andExpect(jsonPath("$.students[0].finalEnabled").value(false));
    }

    @Test
    void studentOverTheAbsenceLimitIsFreeAndOneWithinTheLimitIsNot() throws Exception {
        Percentage limit = Percentage.ofPoints(new BigDecimal("25"));
        Classroom freeStudent = classroom("grid5", "8100005", persistScheme(2, true, null), limit);
        loadAttendance(freeStudent, 2, AttendanceStatus.PRESENT);
        loadAttendance(freeStudent, 3, AttendanceStatus.ABSENT);
        loadAttendance(freeStudent, 4, AttendanceStatus.ABSENT);
        Classroom withinLimit = classroom("grid6", "8100006", persistScheme(2, true, null), limit);
        loadAttendance(withinLimit, 2, AttendanceStatus.PRESENT);
        loadAttendance(withinLimit, 3, AttendanceStatus.PRESENT);
        loadAttendance(withinLimit, 4, AttendanceStatus.PRESENT);
        loadAttendance(withinLimit, 5, AttendanceStatus.ABSENT);

        getAs(freeStudent.teacher(), gridPath(freeStudent))
                .andExpect(jsonPath("$.students[0].condition").value("FREE"));
        getAs(withinLimit.teacher(), gridPath(withinLimit))
                .andExpect(jsonPath("$.students[0].condition").value("IN_PROGRESS"));
    }

    @Test
    void sectionWithoutSchemeReturnsNoColumnsButStillListsTheStudents() throws Exception {
        Classroom classroom = classroom("grid7", "8100007", null, null);

        getAs(classroom.teacher(), gridPath(classroom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationSchemeName").doesNotExist())
                .andExpect(jsonPath("$.columns.length()").value(0))
                .andExpect(jsonPath("$.students.length()").value(1));
    }

    @Test
    void teacherOfAnotherSectionIsForbidden() throws Exception {
        Classroom classroom = classroom("grid8", "8100008", persistScheme(2, true, null), null);
        Person stranger = persistPerson("grid8.stranger", "81000089", "DOCENTE");
        persistInstructor(stranger);

        getAs(stranger, gridPath(classroom)).andExpect(status().isForbidden());
    }

    @Test
    void nonTeacherRolesAreForbidden() throws Exception {
        Classroom classroom = classroom("grid9", "8100009", persistScheme(2, true, null), null);
        Person preceptor = persistPerson("grid9.preceptor", "81000099", "PRECEPTOR");

        getAs(classroom.student(), gridPath(classroom)).andExpect(status().isForbidden());
        getAs(preceptor, gridPath(classroom)).andExpect(status().isForbidden());
    }

    @Test
    void unknownSectionIsNotFound() throws Exception {
        Classroom classroom = classroom("grid10", "8100010", persistScheme(2, true, null), null);

        getAs(classroom.teacher(), "/program-sections/999999999/grade-grid").andExpect(status().isNotFound());
        getAs(classroom.teacher(), "/course-sections/999999999/grade-grid").andExpect(status().isNotFound());
    }

    private record CourseClassroom(Person teacher, CourseSubjectSection section, CourseSubjectSectionEnrollment enrollment) {
    }

    private CourseClassroom courseClassroom(String key, String dniBase, EvaluationScheme scheme) {
        Person teacher = persistPerson(key + ".teacher", dniBase + "1", "DOCENTE");
        Instructor instructor = persistInstructor(teacher);
        Student student = persistStudent(persistPerson(key + ".student", dniBase + "2", "ALUMNO"));
        Term term = persistTerm(null, null);
        Course course = new Course();
        course.setName("Curso " + key);
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName("Modulo " + key);
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision " + key);
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        section.setEvaluationScheme(scheme);
        entityManager.persist(section);
        CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourseEnrollment(courseEnrollment);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
        return new CourseClassroom(teacher, section, enrollment);
    }

    @Test
    void courseSectionGridUsesTheSchemeColumnsWithoutDatesAndShowsLoadedNotes() throws Exception {
        EvaluationScheme scheme = persistScheme(1, false, null);
        scheme.setTpCountExpected(2);
        CourseClassroom classroom = courseClassroom("gridc1", "8200001", scheme);
        postJson(classroom.teacher(), "/course-section-enrollments/" + classroom.enrollment().getId() + "/grade-items",
                new CreateGradeItemRequest(GradeItemType.TP, 2, null, LocalDateTime.of(2026, 6, 1, 10, 0),
                        new BigDecimal("9"), null, null))
                .andExpect(status().isCreated());

        getAs(classroom.teacher(), "/course-sections/" + classroom.section().getId() + "/grade-grid")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(3))
                .andExpect(jsonPath("$.columns[0].type").value("EXAM"))
                .andExpect(jsonPath("$.columns[1].type").value("TP"))
                .andExpect(jsonPath("$.columns[2].sequenceNumber").value(2))
                .andExpect(jsonPath("$.columns[0].date").doesNotExist())
                .andExpect(jsonPath("$.students[0].enrollmentId").value(classroom.enrollment().getId()))
                .andExpect(jsonPath("$.students[0].grades[0].type").value("TP"))
                .andExpect(jsonPath("$.students[0].grades[0].score").value(9.0));
    }

    @Test
    void teacherOfAnotherCourseSectionIsForbidden() throws Exception {
        CourseClassroom classroom = courseClassroom("gridc2", "8200002", persistScheme(1, false, null));
        Person stranger = persistPerson("gridc2.stranger", "82000029", "DOCENTE");
        persistInstructor(stranger);

        getAs(stranger, "/course-sections/" + classroom.section().getId() + "/grade-grid").andExpect(status().isForbidden());
    }
}
