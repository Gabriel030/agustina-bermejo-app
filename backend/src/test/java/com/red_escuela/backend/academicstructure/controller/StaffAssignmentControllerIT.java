package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static com.red_escuela.backend.shared.persistence.QueryCountAssertions.assertAtMostQueries;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffAssignmentControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    private Person teacher;
    private Person otherTeacher;
    private Person teacherWithoutAssignments;
    private Person administrative;
    private Person director;
    private Person preceptor;
    private Person student;

    @BeforeEach
    void persistScenario() {
        teacher = persistPerson("asg.teacher", "92000001", "DOCENTE");
        otherTeacher = persistPerson("asg.other", "92000002", "DOCENTE");
        teacherWithoutAssignments = persistPerson("asg.empty", "92000003", "DOCENTE");
        administrative = persistPerson("asg.admin", "92000004", "ADMINISTRATIVO");
        director = persistPerson("asg.director", "92000005", "DIRECTOR");
        preceptor = persistPerson("asg.preceptor", "92000006", "PRECEPTOR");
        student = persistPerson("asg.student", "92000007", "ALUMNO");
        Instructor instructor = persistInstructor(teacher);
        persistInstructor(otherTeacher);
        persistInstructor(teacherWithoutAssignments);

        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        Classroom classroom = new Classroom();
        classroom.setName("Aula 7");
        classroom.setCapacity(30);
        entityManager.persist(classroom);

        SubjectSection activeProgram = persistProgramSection("Algebra", "A", term, StatusType.ACTIVE);
        persistProgramLink(activeProgram, instructor, InstructorRole.LEAD, 6);
        persistProgramSlot(activeProgram, classroom, DayOfWeek.WEDNESDAY, 18);
        persistProgramSlot(activeProgram, classroom, DayOfWeek.MONDAY, 18);
        SubjectSection closedProgram = persistProgramSection("Fisica", "B", term, StatusType.INACTIVE);
        persistProgramLink(closedProgram, instructor, InstructorRole.LAB, 3);
        SubjectSection otherProgram = persistProgramSection("Quimica", "C", term, StatusType.ACTIVE);
        persistProgramLink(otherProgram, persistInstructorOf(otherTeacher), InstructorRole.LEAD, 9);

        CourseSubjectSection courseSection = persistCourseSection("Modulo 1", "Curso A", term);
        CourseSubjectSectionInstructor courseLink = new CourseSubjectSectionInstructor();
        courseLink.setSection(courseSection);
        courseLink.setInstructor(instructor);
        courseLink.setRole(InstructorRole.ASSISTANT);
        courseLink.setHoursPerWeek(4);
        entityManager.persist(courseLink);
        SectionScheduleSlot courseSlot = new SectionScheduleSlot();
        courseSlot.setCourseSection(courseSection);
        courseSlot.setClassroom(classroom);
        courseSlot.setDayOfWeek(DayOfWeek.FRIDAY);
        courseSlot.setStartTime(LocalTime.of(10, 0));
        courseSlot.setEndTime(LocalTime.of(12, 0));
        entityManager.persist(courseSlot);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void directorSeesProgramAndCourseAssignmentsWithSlotsAndTotalHours() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(2)))
                .andExpect(jsonPath("$.totalWeeklyHours").value(10))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].role").value("LEAD"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].kind").value("PROGRAM"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].hoursPerWeek").value(6))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].termDescription").value("2026-1C"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].sectionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].slots[1].dayOfWeek").value("WEDNESDAY"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Algebra')].slots[0].classroomName").value("Aula 7"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Modulo 1')].kind").value("COURSE"))
                .andExpect(jsonPath("$.assignments[?(@.subjectName=='Modulo 1')].slots[0].startTime").value("10:00:00"));
    }

    @Test
    void includeClosedAddsInactiveSectionsToListAndTotal() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).param("includeClosed", "true")
                        .header("Authorization", bearer(administrative)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(3)))
                .andExpect(jsonPath("$.totalWeeklyHours").value(13));
    }

    @Test
    void teacherWithoutAssignmentsGetsEmptyList() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacherWithoutAssignments.getId())
                        .header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(0)))
                .andExpect(jsonPath("$.totalWeeklyHours").value(0));
    }

    @Test
    void teacherSeesOwnAssignments() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments", hasSize(2)));
    }

    @Test
    void teacherCannotSeeAnotherTeacherAndGetsEmpty404() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void administrativeCannotSeeAdministrativeStaffRecord() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", administrative.getId())
                        .header("Authorization", bearer(administrative)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/staff/{id}/assignments", director.getId()).header("Authorization", bearer(administrative)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void unknownPersonAndStudentPersonGetEmpty404() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", 987654321L).header("Authorization", bearer(director)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
        mockMvc.perform(get("/staff/{id}/assignments", student.getId()).header("Authorization", bearer(director)))
                .andExpect(status().isNotFound());
    }

    @Test
    void preceptorStudentAndAnonymousAreRejected() throws Exception {
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", bearer(preceptor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignmentsAreLoadedInConstantNumberOfQueries() {
        String token = bearer(director);
        assertAtMostQueries(entityManagerFactory, 12, () -> {
            try {
                mockMvc.perform(get("/staff/{id}/assignments", teacher.getId()).header("Authorization", token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.assignments", hasSize(2)));
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return null;
        });
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private SubjectSection persistProgramSection(String subjectName, String sectionName, Term term, StatusType status) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(subjectName);
        entityManager.persist(subject);
        SubjectSection section = new SubjectSection();
        section.setName(sectionName);
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(30);
        section.setStatus(status);
        entityManager.persist(section);
        return section;
    }

    private void persistProgramLink(SubjectSection section, Instructor instructor, InstructorRole role, int hours) {
        SubjectSectionInstructor link = new SubjectSectionInstructor();
        link.setSection(section);
        link.setInstructor(instructor);
        link.setRole(role);
        link.setHoursPerWeek(hours);
        entityManager.persist(link);
    }

    private void persistProgramSlot(SubjectSection section, Classroom classroom, DayOfWeek day, int startHour) {
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(startHour + 2, 0));
        entityManager.persist(slot);
    }

    private CourseSubjectSection persistCourseSection(String moduleName, String sectionName, Term term) {
        Course course = new Course();
        course.setName("Curso " + moduleName);
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(moduleName);
        entityManager.persist(courseSubject);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName(sectionName);
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private Instructor persistInstructorOf(Person person) {
        return entityManager.createQuery("select i from Instructor i where i.person.id = :id", Instructor.class)
                .setParameter("id", person.getId()).getSingleResult();
    }

    private Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Asg");
        person.setLastname(username);
        person.setUsername(username);
        person.setDni(dni);
        person.setEmail(username + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
