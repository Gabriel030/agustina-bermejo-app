package com.red_escuela.backend.characterization.coursework;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MeScheduleIntegrationTest extends CourseworkCharacterizationSupport {

    private void enroll(Student student, String key, String schedule, EnrollmentStatus status) {
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
        section.setSchedule(schedule);
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
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
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        entityManager.persist(enrollment);
        entityManager.flush();
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void returnsOnlyOwnActiveSections() throws Exception {
        Person mine = persistPerson("sched.mine", "71000001", "ALUMNO");
        Student student = persistStudent(mine);
        Student other = persistStudent(persistPerson("sched.other", "71000002", "ALUMNO"));
        enroll(student, "A", "Lun 18:00", EnrollmentStatus.IN_PROGRESS);
        enroll(student, "B", "Mie 19:00", EnrollmentStatus.ENROLLED);
        enroll(other, "C", "Vie 10:00", EnrollmentStatus.IN_PROGRESS);

        JsonNode body = bodyOf(getAs(mine, "/me/schedule").andExpect(status().isOk()));

        assertThat(body.get("items")).hasSize(2);
        assertThat(body.get("items").findValuesAsString("schedule")).containsExactlyInAnyOrder("Lun 18:00", "Mie 19:00");
        assertThat(body.get("generatedAt").asString()).isNotBlank();
    }

    @Test
    void responseHasNoGradeOrMoneyFields() throws Exception {
        Person mine = persistPerson("sched.keys", "71000003", "ALUMNO");
        enroll(persistStudent(mine), "K", "Lun 18:00", EnrollmentStatus.REGULAR);

        JsonNode body = bodyOf(getAs(mine, "/me/schedule").andExpect(status().isOk()));

        assertThat(fieldNames(body)).containsExactlyInAnyOrder("generatedAt", "items");
        assertThat(fieldNames(body.get("items").get(0))).containsExactlyInAnyOrder(
                "sectionEnrollmentId", "courseName", "sectionName", "courseSubjectName", "schedule", "instructorTitular");
    }

    @Test
    void droppedSectionsAreExcluded() throws Exception {
        Person mine = persistPerson("sched.drop", "71000004", "ALUMNO");
        Student student = persistStudent(mine);
        enroll(student, "W", "Lun 18:00", EnrollmentStatus.WITHDRAWN);
        enroll(student, "Q", "Mar 18:00", EnrollmentStatus.WAITLISTED);
        enroll(student, "P", "Jue 18:00", EnrollmentStatus.APPROVED);

        JsonNode body = bodyOf(getAs(mine, "/me/schedule").andExpect(status().isOk()));

        assertThat(body.get("items")).isEmpty();
    }

    @Test
    void staffWithoutStudentRecordGetsEmptyList() throws Exception {
        Person director = persistPerson("sched.director", "71000005", "DIRECTOR");

        JsonNode body = bodyOf(getAs(director, "/me/schedule").andExpect(status().isOk()));

        assertThat(body.get("items")).isEmpty();
    }

    @Test
    void platformAdminIsForbidden() throws Exception {
        Person admin = persistPerson("sched.admin", "71000006", "PLATFORM_ADMIN");

        getAs(admin, "/me/schedule").andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/me/schedule")).andExpect(status().isUnauthorized());
    }
}
