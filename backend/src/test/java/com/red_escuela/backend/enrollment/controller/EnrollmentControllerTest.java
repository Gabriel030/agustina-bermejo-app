package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.testsupport.TermFixtures;
import com.red_escuela.backend.config.TestSubjectCodes;

import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.enrollment.events.SubjectWaitlistPromotedEvent;

import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.SectionScheduleSlot;
import com.red_escuela.backend.enrollment.events.SubjectEnrollmentWithdrawnEvent;
import com.red_escuela.backend.enrollment.events.SubjectSectionChangedEvent;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import java.time.DayOfWeek;
import java.time.LocalTime;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class EnrollmentControllerTest extends AbstractIntegrationTest {
    private static final String STAFF_REASON = "Pedido del alumno en secretaría";
    private static final String STAFF_REASON_BODY = "{\"reason\":\"" + STAFF_REASON + "\"}";

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(String dni, StudentOnboardingStatus onboardingStatus, StatusType personStatus, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(personStatus);
        entityManager.persist(person);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(onboardingStatus);
        entityManager.persist(student);
        return student;
    }

    private String tokenForStudent(Student student) {
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        student.getPerson().getRoles().add(role);
        entityManager.persist(student.getPerson());
        entityManager.flush();
        return jwtService.generateAccessToken(student.getPerson());
    }

    private Program persistProgram(String name, int totalCredits) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(totalCredits);
        entityManager.persist(program);
        return program;
    }

    private ProgramCohort persistProgramCohort(Program program, java.time.LocalDate admissionStart, java.time.LocalDate admissionEnd) {
        Person systemActor = persistSystemActor();
        Term term = persistTerm(null, null);

        com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate template = new com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(systemActor);
        entityManager.persist(template);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(com.red_escuela.backend.billing.entity.CohortIntake.FIRST);
        cohort.setAdmissionStart(admissionStart);
        cohort.setAdmissionEnd(admissionEnd);
        cohort.setCursadaStart(java.time.LocalDate.now());
        cohort.setCreatedBy(systemActor);
        entityManager.persist(cohort);
        return cohort;
    }

    private Person persistSystemActor() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Sistema");
        person.setLastname("Test");
        person.setDni("SYS" + System.nanoTime());
        person.setUsername("system-test-" + System.nanoTime());
        person.setEmail("system-test-" + System.nanoTime() + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private ProgramSubject persistProgramSubject(Program program, Subject subject, int credits) {
        ProgramSubject ps = new ProgramSubject();
        ps.setProgram(program);
        ps.setSubject(subject);
        ps.setCredits(credits);
        ps.setCurricularYear(1);
        ps.setCurricularTerm(CurricularTerm.FIRST);
        ps.setStatus(StatusType.ACTIVE);
        entityManager.persist(ps);
        return ps;
    }

    private Term persistTerm(LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(enrollmentStart != null ? enrollmentStart : TermFixtures.OPEN_WINDOW_START);
        term.setEnrollmentEnd(enrollmentEnd != null ? enrollmentEnd : TermFixtures.OPEN_WINDOW_END);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSubjectSection(Subject subject, Term term, Integer capacity) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private ProgramSubjectPrerequisite persistPrerequisite(ProgramSubject target, ProgramSubject prerequisite, RequirementType type) {
        ProgramSubjectPrerequisite prereq = new ProgramSubjectPrerequisite();
        prereq.setProgramSubject(target);
        prereq.setPrerequisiteProgramSubject(prerequisite);
        prereq.setRequirementType(type);
        entityManager.persist(prereq);
        return prereq;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private SubjectSectionEnrollment persistSectionEnrollment(
            Student student, ProgramEnrollment programEnrollment, SubjectSection section, EnrollmentStatus status, int attemptNumber) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(attemptNumber);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Course persistCourse(Term term, LocalDateTime registrationStart, LocalDateTime registrationEnd) {
        Course course = new Course();
        course.setName("Curso de prueba");
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course.setRegistrationStart(registrationStart);
        course.setRegistrationEnd(registrationEnd);
        entityManager.persist(course);
        return course;
    }

    private CourseSubject persistCourseSubject(Course course, String name) {
        CourseSubject courseSubject = new CourseSubject();
        courseSubject.setCourse(course);
        courseSubject.setName(name);
        courseSubject.setStatus(StatusType.ACTIVE);
        courseSubject.setSortOrder(1);
        entityManager.persist(courseSubject);
        return courseSubject;
    }

    private CourseSubjectSection persistCourseSubjectSection(CourseSubject courseSubject, Term term, Integer capacity) {
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName(courseSubject.getName() + " - Com A");
        section.setCourseSubject(courseSubject);
        section.setTerm(term);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    @Test
    void enrollingProspectReturns400() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000001", StudentOnboardingStatus.PROSPECTO, StatusType.ACTIVE, institution);
        Program program = persistProgram("Tecnicatura", 100);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc1", "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateProgramEnrollmentRequest(program.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollingTwiceInTheSameProgramReturns409() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000002", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Program program = persistProgram("Tecnicatura2", 100);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc2", "PRECEPTOR");
        CreateProgramEnrollmentRequest request = new CreateProgramEnrollmentRequest(program.getId());

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void enrollingOutsideAdmissionWindowReturns409() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000003", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        java.time.LocalDate today = java.time.LocalDate.now();
        Program program = persistProgram("Tecnicatura3", 100);
        ProgramCohort cohort = persistProgramCohort(program, today.minusDays(30), today.minusDays(10));
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc3", "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateProgramEnrollmentRequest(program.getId(), cohort.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void enrollingWithoutEnrolledProgramEnrollmentReturns400() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000004", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Program program = persistProgram("Tecnicatura4", 100);
        Subject subject = persistSubject("Materia4");
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.WITHDRAWN);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc4", "PRECEPTOR");

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollingWithoutMeetingPrerequisiteReturns400AndMetInAnotherProgramIsStill400() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000005", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject compartida = persistSubject("Física I compartida");
        Subject avanzada = persistSubject("Física II");

        Program carreraA = persistProgram("Carrera A", 100);
        ProgramSubject psFisicaIA = persistProgramSubject(carreraA, compartida, 10);
        ProgramSubject psFisicaIIA = persistProgramSubject(carreraA, avanzada, 10);
        persistPrerequisite(psFisicaIIA, psFisicaIA, RequirementType.APPROVED);

        Program carreraB = persistProgram("Carrera B", 100);
        ProgramSubject psFisicaIB = persistProgramSubject(carreraB, compartida, 10);
        ProgramSubject psFisicaIIB = persistProgramSubject(carreraB, avanzada, 10);
        persistPrerequisite(psFisicaIIB, psFisicaIB, RequirementType.APPROVED);

        Term term = persistTerm(null, null);
        SubjectSection seccionAvanzadaA = persistSubjectSection(avanzada, term, null);
        SubjectSection seccionAvanzadaB = persistSubjectSection(avanzada, term, null);
        SubjectSection seccionCompartidaA = persistSubjectSection(compartida, term, null);

        ProgramEnrollment enrollmentA = persistProgramEnrollment(student, carreraA, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment enrollmentB = persistProgramEnrollment(student, carreraB, ProgramEnrollmentStatus.ENROLLED);
        persistSectionEnrollment(student, enrollmentA, seccionCompartidaA, EnrollmentStatus.APPROVED, 1);
        entityManager.flush();

        String token = tokenWithRole("preceptor-insc5", "PRECEPTOR");

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollmentA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(seccionAvanzadaA.getId()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollmentB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(seccionAvanzadaB.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollingInTwoSectionsOfTheSameSubjectReturns409AndChangeSectionReturns200SameAttemptNumber() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000006", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia6");
        Program program = persistProgram("Carrera6", 100);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection seccionA = persistSubjectSection(subject, term, null);
        SubjectSection seccionB = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc6", "PRECEPTOR");

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(seccionA.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(1));

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(seccionB.getId()))))
                .andExpect(status().isConflict());

        List<SubjectSectionEnrollment> filas = entityManager
                .createQuery("select e from SubjectSectionEnrollment e where e.programEnrollment.id = :peid", SubjectSectionEnrollment.class)
                .setParameter("peid", enrollment.getId())
                .getResultList();
        Long idOrigen = filas.get(0).getId();

        mockMvc.perform(post("/section-enrollments/{id}/change-section", idOrigen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new ChangeSectionRequest(seccionB.getId(), STAFF_REASON))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.sectionId").value(seccionB.getId()));
    }

    @Test
    void fullCapacityLeavesWaitlistedAndWithdrawalPromotesTheNext() throws Exception {
        Institution institution = persistInstitution();
        Student estudiante1 = persistStudent("50000007", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Student estudiante2 = persistStudent("50000008", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia7");
        Program program = persistProgram("Carrera7", 100);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 1);
        ProgramEnrollment enrollment1 = persistProgramEnrollment(estudiante1, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment enrollment2 = persistProgramEnrollment(estudiante2, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc7", "PRECEPTOR");

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        String idWaitlisted = mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITLISTED"))
                .andReturn().getResponse().getContentAsString();
        Long waitlistedId = objectMapper.readTree(idWaitlisted).get("id").asLong();

        List<SubjectSectionEnrollment> filasEnrollment1 = entityManager
                .createQuery("select e from SubjectSectionEnrollment e where e.programEnrollment.id = :peid", SubjectSectionEnrollment.class)
                .setParameter("peid", enrollment1.getId())
                .getResultList();
        Long idEnrolled = filasEnrollment1.get(0).getId();

        mockMvc.perform(post("/section-enrollments/{id}/withdraw", idEnrolled)
                        .contentType(MediaType.APPLICATION_JSON).content(STAFF_REASON_BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        SubjectSectionEnrollment promovida = entityManager.find(SubjectSectionEnrollment.class, waitlistedId);
        assertThat(promovida.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void changeSectionToTheSameSectionIsNoOpAndDoesNotLoseThePlaceAgainstTheWaitlist() throws Exception {
        Institution institution = persistInstitution();
        Student estudiante1 = persistStudent("50000017", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Student estudiante2 = persistStudent("50000018", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia17");
        Program program = persistProgram("Carrera17", 100);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, 1);
        ProgramEnrollment enrollment1 = persistProgramEnrollment(estudiante1, program, ProgramEnrollmentStatus.ENROLLED);
        ProgramEnrollment enrollment2 = persistProgramEnrollment(estudiante2, program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrolled = persistSectionEnrollment(estudiante1, enrollment1, section, EnrollmentStatus.ENROLLED, 1);
        SubjectSectionEnrollment waitlisted = persistSectionEnrollment(estudiante2, enrollment2, section, EnrollmentStatus.WAITLISTED, 1);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc17", "PRECEPTOR");

        mockMvc.perform(post("/section-enrollments/{id}/change-section", enrolled.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new ChangeSectionRequest(section.getId(), STAFF_REASON))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(enrolled.getId()))
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, enrolled.getId()).getStatus())
                .isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(entityManager.find(SubjectSectionEnrollment.class, waitlisted.getId()).getStatus())
                .isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void withdrawalWithFinalGradeLoadedReturns409() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000009", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia9");
        Program program = persistProgram("Carrera9", 100);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment sectionEnrollment = persistSectionEnrollment(student, enrollment, section, EnrollmentStatus.APPROVED, 1);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc9", "PRECEPTOR");

        mockMvc.perform(post("/section-enrollments/{id}/withdraw", sectionEnrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(STAFF_REASON_BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawalAndReEnrollmentToTheSameSectionReactivatesTheSameRow() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000010", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia10");
        Program program = persistProgram("Carrera10", 100);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc10", "PRECEPTOR");

        String creada = mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idOriginal = objectMapper.readTree(creada).get("id").asLong();

        mockMvc.perform(post("/section-enrollments/{id}/withdraw", idOriginal)
                        .contentType(MediaType.APPLICATION_JSON).content(STAFF_REASON_BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/program-enrollments/{id}/section-enrollments", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateSectionEnrollmentRequest(section.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(idOriginal))
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        long totalFilas = entityManager
                .createQuery("select count(e) from SubjectSectionEnrollment e where e.subjectSection.id = :sid", Long.class)
                .setParameter("sid", section.getId())
                .getSingleResult();
        assertThat(totalFilas).isEqualTo(1L);
    }

    @Test
    void enrollingCourseWithModuleWithoutSectionReturns409WithoutCreatingCourseEnrollment() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000011", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Term term = persistTerm(null, null);
        Course course = persistCourse(term, null, null);
        persistCourseSubject(course, "Módulo sin comisión");
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc11", "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/course-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isConflict());

        List<CourseEnrollment> creados = courseEnrollmentRepository.findByStudentId(student.getId());
        assertThat(creados).isEmpty();
    }

    @Test
    void enrollingCourseWithAmbiguousModuleWithoutOverridesReturns400AndWithOverridesReturns201() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000012", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Term term = persistTerm(null, null);
        Course course = persistCourse(term, null, null);
        CourseSubject modulo = persistCourseSubject(course, "Módulo ambiguo");
        CourseSubjectSection seccionA = persistCourseSubjectSection(modulo, term, null);
        CourseSubjectSection seccionB = persistCourseSubjectSection(modulo, term, null);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc12", "PRECEPTOR");

        mockMvc.perform(post("/students/{id}/course-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/students/{id}/course-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(
                                new CreateCourseEnrollmentRequest(course.getId(), Map.of(modulo.getId(), seccionB.getId())))))
                .andExpect(status().isCreated());
    }

    @Test
    void progressWithEverythingApprovedIsEligibleToCompleteWithoutChangingStatus() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000013", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia13");
        Program program = persistProgram("Carrera13", 10);
        persistProgramSubject(program, subject, 10);
        Term term = persistTerm(null, null);
        SubjectSection section = persistSubjectSection(subject, term, null);
        ProgramEnrollment enrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        persistSectionEnrollment(student, enrollment, section, EnrollmentStatus.APPROVED, 1);
        entityManager.flush();
        String token = tokenWithRole("preceptor-insc13", "PRECEPTOR");

        mockMvc.perform(get("/program-enrollments/{id}/progress", enrollment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elegibleParaCompletar").value(true))
                .andExpect(jsonPath("$.creditosAcumulados").value(10));

        entityManager.clear();
        ProgramEnrollment reloaded = entityManager.find(ProgramEnrollment.class, enrollment.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void meProgressWithAnotherStudentsIdReturns404() throws Exception {
        Institution institution = persistInstitution();
        Student duenio = persistStudent("50000014", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Student otro = persistStudent("50000015", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Program program = persistProgram("Carrera14", 100);
        ProgramEnrollment enrollment = persistProgramEnrollment(duenio, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        String tokenOtro = tokenForStudent(otro);

        mockMvc.perform(get("/me/program-enrollments/{id}/progress", enrollment.getId())
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());
    }

    @Test
    void enrollmentEndpointsWithoutCorrectRoleReturn403AndMeWithAnyRoleWorks() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000016", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Program program = persistProgram("Carrera16", 100);
        entityManager.flush();
        String tokenAlumno = tokenForStudent(student);

        mockMvc.perform(post("/students/{id}/program-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenAlumno)
                        .content(objectMapper.writeValueAsString(new CreateProgramEnrollmentRequest(program.getId()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/me/program-enrollments").header("Authorization", "Bearer " + tokenAlumno))
                .andExpect(status().isOk());
    }

    @Test
    void withdrawalAndReEnrollmentToTheSameCourseReactivatesTheSubEnrollmentsWithoutViolatingUniqueConstraint() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("50000019", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Term term = persistTerm(null, null);
        Course course = persistCourse(term, null, null);
        CourseSubject modulo = persistCourseSubject(course, "Módulo19");
        CourseSubjectSection seccion = persistCourseSubjectSection(modulo, term, null);
        entityManager.flush();
        String token = tokenWithRole("administrativo-insc19", "ADMINISTRATIVO");

        String primeraMatricula = mockMvc.perform(post("/students/{id}/course-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long primerCourseEnrollmentId = objectMapper.readTree(primeraMatricula).get("id").asLong();

        mockMvc.perform(post("/course-enrollments/{id}/withdraw", primerCourseEnrollmentId)
                        .contentType(MediaType.APPLICATION_JSON).content(STAFF_REASON_BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/students/{id}/course-enrollments", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isCreated());

        long totalFilas = entityManager
                .createQuery("select count(e) from CourseSubjectSectionEnrollment e where e.courseSubjectSection.id = :sid", Long.class)
                .setParameter("sid", seccion.getId())
                .getSingleResult();
        assertThat(totalFilas).isEqualTo(1L);
    }

    @Test
    void courseWithdrawalFreesItsModulesCapacityAndPromotesTheNextInWaitlist() throws Exception {
        Institution institution = persistInstitution();
        Student estudiante1 = persistStudent("50000020", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Student estudiante2 = persistStudent("50000021", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Term term = persistTerm(null, null);
        Course course = persistCourse(term, null, null);
        CourseSubject modulo = persistCourseSubject(course, "Módulo20");
        CourseSubjectSection seccion = persistCourseSubjectSection(modulo, term, 1);
        entityManager.flush();
        String token = tokenWithRole("administrativo-insc20", "ADMINISTRATIVO");

        String matricula1 = mockMvc.perform(post("/students/{id}/course-enrollments", estudiante1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long courseEnrollment1Id = objectMapper.readTree(matricula1).get("id").asLong();

        mockMvc.perform(post("/students/{id}/course-enrollments", estudiante2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(new CreateCourseEnrollmentRequest(course.getId(), null))))
                .andExpect(status().isCreated());

        CourseSubjectSectionEnrollment subDeEstudiante2 = entityManager
                .createQuery("select e from CourseSubjectSectionEnrollment e where e.student.id = :sid", CourseSubjectSectionEnrollment.class)
                .setParameter("sid", estudiante2.getId())
                .getSingleResult();
        assertThat(subDeEstudiante2.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        mockMvc.perform(post("/course-enrollments/{id}/withdraw", courseEnrollment1Id)
                        .contentType(MediaType.APPLICATION_JSON).content(STAFF_REASON_BODY)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        CourseSubjectSectionEnrollment promovida = entityManager.find(CourseSubjectSectionEnrollment.class, subDeEstudiante2.getId());
        assertThat(promovida.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    private static final LocalDateTime FIRST_PERIOD_START = LocalDateTime.of(2026, 3, 1, 0, 0);
    private static final LocalDateTime FIRST_PERIOD_END = LocalDateTime.of(2026, 7, 31, 23, 59);
    private static final LocalDateTime SECOND_PERIOD_START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime SECOND_PERIOD_END = LocalDateTime.of(2026, 12, 15, 23, 59);

    private record ChangeScenario(SubjectSectionEnrollment origin, SubjectSection destination, SubjectSection otherSection, String token) {
    }

    private Term persistTermBetween(LocalDateTime start, LocalDateTime end) {
        Term term = persistTerm(null, null);
        term.setStartDate(start);
        term.setEndDate(end);
        return term;
    }

    private void persistSlot(SubjectSection section, DayOfWeek day, LocalTime start, LocalTime end) {
        Classroom classroom = new Classroom();
        classroom.setName("Aula " + section.getName() + day + start);
        classroom.setCapacity(30);
        entityManager.persist(classroom);
        SectionScheduleSlot slot = new SectionScheduleSlot();
        slot.setSubjectSection(section);
        slot.setClassroom(classroom);
        slot.setDayOfWeek(day);
        slot.setStartTime(start);
        slot.setEndTime(end);
        entityManager.persist(slot);
    }

    private ChangeScenario changeScenario(String key, Term otherSectionTerm, LocalTime destinationStart, LocalTime destinationEnd, String role) {
        Institution institution = persistInstitution();
        Student student = persistStudent(key, StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject changing = persistSubject("Cambiante" + key);
        Subject other = persistSubject("Algebra" + key);
        Program program = persistProgram("Carrera" + key, 100);
        persistProgramSubject(program, changing, 10);
        persistProgramSubject(program, other, 10);
        Term term = persistTermBetween(FIRST_PERIOD_START, FIRST_PERIOD_END);
        SubjectSection originSection = persistSubjectSection(changing, term, 10);
        SubjectSection destination = persistSubjectSection(changing, term, 10);
        destination.setName("Comision destino " + key);
        SubjectSection otherSection = persistSubjectSection(other, otherSectionTerm == null ? term : otherSectionTerm, 10);
        ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment origin = persistSectionEnrollment(student, programEnrollment, originSection, EnrollmentStatus.ENROLLED, 1);
        persistSectionEnrollment(student, programEnrollment, otherSection, EnrollmentStatus.ENROLLED, 1);
        persistSlot(otherSection, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        persistSlot(destination, DayOfWeek.MONDAY, destinationStart, destinationEnd);
        entityManager.flush();
        return new ChangeScenario(origin, destination, otherSection, tokenWithRole("staff-" + key, role));
    }

    private org.springframework.test.web.servlet.ResultActions changeSection(ChangeScenario scenario, String reason) throws Exception {
        return mockMvc.perform(post("/section-enrollments/{id}/change-section", scenario.origin().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + scenario.token())
                .content(objectMapper.writeValueAsString(new ChangeSectionRequest(scenario.destination().getId(), reason))));
    }

    @Test
    void staffChangeSectionRejectsAScheduleThatOverlapsAnotherActiveCursadaNamingTheSubject() throws Exception {
        ChangeScenario scenario = changeScenario("60000001", null, LocalTime.of(9, 0), LocalTime.of(11, 0), "PRECEPTOR");

        changeSection(scenario, STAFF_REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_SCHEDULE_OVERLAP"))
                .andExpect(jsonPath("$.details.conflictingSubject").value("Algebra60000001"))
                .andExpect(jsonPath("$.details.day").value("lunes"));

        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, scenario.origin().getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void staffChangeSectionAllowsASlotThatStartsExactlyWhenTheOtherEnds() throws Exception {
        ChangeScenario scenario = changeScenario("60000002", null, LocalTime.of(10, 0), LocalTime.of(12, 0), "PRECEPTOR");

        changeSection(scenario, STAFF_REASON).andExpect(status().isOk());
    }

    @Test
    void staffChangeSectionRejectsASlotThatEndsExactlyOneMinuteAfterTheOtherStarts() throws Exception {
        ChangeScenario scenario = changeScenario("60000003", null, LocalTime.of(6, 0), LocalTime.of(8, 1), "PRECEPTOR");

        changeSection(scenario, STAFF_REASON).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SECTION_SCHEDULE_OVERLAP"));
    }

    @Test
    void staffChangeSectionAllowsTheSameScheduleWhenTheOtherCursadaIsInAnotherPeriod() throws Exception {
        Term laterTerm = persistTermBetween(SECOND_PERIOD_START, SECOND_PERIOD_END);
        ChangeScenario scenario = changeScenario("60000004", laterTerm, LocalTime.of(8, 0), LocalTime.of(10, 0), "PRECEPTOR");

        changeSection(scenario, STAFF_REASON).andExpect(status().isOk());
    }

    @Test
    void staffChangeSectionAllowsOverlapWithAWithdrawnCursada() throws Exception {
        ChangeScenario scenario = changeScenario("60000005", null, LocalTime.of(9, 0), LocalTime.of(11, 0), "PRECEPTOR");
        entityManager.createQuery("update SubjectSectionEnrollment e set e.status = :status where e.subjectSection.id = :sectionId")
                .setParameter("status", EnrollmentStatus.WITHDRAWN).setParameter("sectionId", scenario.otherSection().getId()).executeUpdate();
        entityManager.clear();

        changeSection(scenario, STAFF_REASON).andExpect(status().isOk());
    }

    @Test
    void staffChangeSectionWithoutReasonReturns400AndDoesNotMoveTheStudent() throws Exception {
        ChangeScenario scenario = changeScenario("60000006", null, LocalTime.of(10, 0), LocalTime.of(12, 0), "DIRECTOR");

        changeSection(scenario, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENROLLMENT_CHANGE_REASON_REQUIRED"));

        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, scenario.origin().getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void directorChangeSectionStoresReasonAndActorAndPublishesOneEventForStudentAndTeachers() throws Exception {
        ChangeScenario scenario = changeScenario("60000007", null, LocalTime.of(10, 0), LocalTime.of(12, 0), "DIRECTOR");

        changeSection(scenario, "  Cambio   por horario laboral ").andExpect(status().isOk());

        entityManager.flush();
        entityManager.clear();
        SubjectSectionEnrollment origin = entityManager.find(SubjectSectionEnrollment.class, scenario.origin().getId());
        Long directorPersonId = entityManager.createQuery("select p.id from Person p where p.username = :u", Long.class)
                .setParameter("u", "staff-60000007").getSingleResult();
        assertThat(origin.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(origin.getWithdrawalReason()).isEqualTo("Cambio por horario laboral");
        assertThat(origin.getWithdrawnByPersonId()).isEqualTo(directorPersonId);
        assertThat(origin.getDroppedAt()).isNotNull();
        List<SubjectSectionChangedEvent> changed = events.stream(SubjectSectionChangedEvent.class).toList();
        assertThat(changed).hasSize(1);
        assertThat(changed.get(0).notifyStudent()).isTrue();
        assertThat(changed.get(0).reason()).isEqualTo("Cambio por horario laboral");
        assertThat(changed.get(0).toSectionName()).isEqualTo("Comision destino 60000007");
    }

    private Long enrolledIdOf(Student student) {
        return entityManager.createQuery("select e.id from SubjectSectionEnrollment e where e.student.id = :id", Long.class)
                .setParameter("id", student.getId()).getResultList().get(0);
    }

    private org.springframework.test.web.servlet.ResultActions withdrawSubject(Long id, String token, String body) throws Exception {
        var request = post("/section-enrollments/{id}/withdraw", id).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private Student studentWithOneEnrollment(String dni) {
        Institution institution = persistInstitution();
        Student student = persistStudent(dni, StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia" + dni);
        Program program = persistProgram("Carrera" + dni, 100);
        persistProgramSubject(program, subject, 10);
        SubjectSection section = persistSubjectSection(subject, persistTerm(null, null), 10);
        persistSectionEnrollment(student, persistProgramEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED), section, EnrollmentStatus.ENROLLED, 1);
        entityManager.flush();
        return student;
    }

    @Test
    void staffWithdrawalWithoutBodyOrReasonReturns400AndKeepsTheEnrollment() throws Exception {
        Student student = studentWithOneEnrollment("60000011");
        Long id = enrolledIdOf(student);
        String token = tokenWithRole("preceptor-60000011", "PRECEPTOR");

        withdrawSubject(id, token, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENROLLMENT_CHANGE_REASON_REQUIRED"));
        withdrawSubject(id, token, "{\"reason\":\"   \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENROLLMENT_CHANGE_REASON_REQUIRED"));

        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, id).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(events.stream(SubjectEnrollmentWithdrawnEvent.class)).isEmpty();
    }

    @Test
    void staffWithdrawalReasonBoundariesAre10And500Characters() throws Exception {
        Student student = studentWithOneEnrollment("60000012");
        Long id = enrolledIdOf(student);
        String token = tokenWithRole("preceptor-60000012", "PRECEPTOR");

        withdrawSubject(id, token, "{\"reason\":\"123456789\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENROLLMENT_CHANGE_REASON_INVALID"));
        withdrawSubject(id, token, "{\"reason\":\"" + "a".repeat(501) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ENROLLMENT_CHANGE_REASON_INVALID"));
        withdrawSubject(id, token, "{\"reason\":\"" + "a".repeat(500) + "\"}").andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, id).getWithdrawalReason()).hasSize(500);
    }

    @Test
    void directorWithdrawalStoresReasonAndActorAndPublishesOneEventNotifyingTheStudent() throws Exception {
        Student student = studentWithOneEnrollment("60000013");
        Long id = enrolledIdOf(student);
        String token = tokenWithRole("director-60000013", "DIRECTOR");

        withdrawSubject(id, token, "{\"reason\":\"Pidió la baja por motivos personales\"}").andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        SubjectSectionEnrollment withdrawn = entityManager.find(SubjectSectionEnrollment.class, id);
        Long directorPersonId = entityManager.createQuery("select p.id from Person p where p.username = :u", Long.class)
                .setParameter("u", "director-60000013").getSingleResult();
        assertThat(withdrawn.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(withdrawn.getWithdrawalReason()).isEqualTo("Pidió la baja por motivos personales");
        assertThat(withdrawn.getWithdrawnByPersonId()).isEqualTo(directorPersonId);
        assertThat(withdrawn.getDroppedAt()).isNotNull();
        List<SubjectEnrollmentWithdrawnEvent> published = events.stream(SubjectEnrollmentWithdrawnEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).notifyStudent()).isTrue();
        assertThat(published.get(0).studentPersonId()).isEqualTo(student.getPerson().getId());
    }

    @Test
    void studentOwnWithdrawalNeedsNoReasonKeepsActorAsTheStudentAndDoesNotNotifyTheStudent() throws Exception {
        Student student = studentWithOneEnrollment("60000014");
        Long id = enrolledIdOf(student);
        String token = tokenForStudent(student);

        mockMvc.perform(post("/me/section-enrollments/{id}/withdraw", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        SubjectSectionEnrollment withdrawn = entityManager.find(SubjectSectionEnrollment.class, id);
        assertThat(withdrawn.getWithdrawalReason()).isNull();
        assertThat(withdrawn.getWithdrawnByPersonId()).isEqualTo(student.getPerson().getId());
        assertThat(events.stream(SubjectEnrollmentWithdrawnEvent.class).toList()).singleElement()
                .satisfies(event -> assertThat(event.notifyStudent()).isFalse());
    }

    private org.springframework.test.web.servlet.ResultActions listCandidates(ChangeScenario scenario) throws Exception {
        return mockMvc.perform(get("/section-enrollments/{id}/change-section-candidates", scenario.origin().getId())
                .header("Authorization", "Bearer " + scenario.token()));
    }

    @Test
    void candidatesTellWhichSectionsCanBeChosenAndWhyTheOthersCannot() throws Exception {
        ChangeScenario scenario = changeScenario("60000021", null, LocalTime.of(9, 0), LocalTime.of(11, 0), "DIRECTOR");
        Subject subject = scenario.origin().getSubjectSection().getSubject();
        Term sameTerm = scenario.origin().getSubjectSection().getTerm();
        SubjectSection free = persistSubjectSection(subject, sameTerm, 10);
        persistSlot(free, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
        SubjectSection full = persistSubjectSection(subject, sameTerm, 0);
        SubjectSection otherPeriod = persistSubjectSection(subject, persistTermBetween(SECOND_PERIOD_START, SECOND_PERIOD_END), 10);
        entityManager.flush();

        listCandidates(scenario).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sectionId==" + scenario.origin().getSubjectSection().getId() + ")].blockReason", org.hamcrest.Matchers.contains("CURRENT_SECTION")))
                .andExpect(jsonPath("$[?(@.sectionId==" + scenario.destination().getId() + ")].blockReason", org.hamcrest.Matchers.contains("SCHEDULE_OVERLAP")))
                .andExpect(jsonPath("$[?(@.sectionId==" + scenario.destination().getId() + ")].conflictingSubject", org.hamcrest.Matchers.contains("Algebra60000021")))
                .andExpect(jsonPath("$[?(@.sectionId==" + scenario.destination().getId() + ")].selectable", org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$[?(@.sectionId==" + full.getId() + ")].blockReason", org.hamcrest.Matchers.contains("SECTION_FULL")))
                .andExpect(jsonPath("$[?(@.sectionId==" + otherPeriod.getId() + ")].blockReason", org.hamcrest.Matchers.contains("OTHER_TERM")))
                .andExpect(jsonPath("$[?(@.sectionId==" + free.getId() + ")].selectable", org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.sectionId==" + free.getId() + ")].availableSeats", org.hamcrest.Matchers.contains(10)))
                .andExpect(jsonPath("$[?(@.sectionId==" + free.getId() + ")].slots[0].dayOfWeek", org.hamcrest.Matchers.contains("TUESDAY")));
    }

    @Test
    void candidateThatTouchesTheOtherCursadaAtTheBoundaryIsSelectable() throws Exception {
        ChangeScenario scenario = changeScenario("60000022", null, LocalTime.of(10, 0), LocalTime.of(12, 0), "PRECEPTOR");

        listCandidates(scenario).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sectionId==" + scenario.destination().getId() + ")].selectable", org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void candidatesOfAnUnknownEnrollmentReturn404() throws Exception {
        mockMvc.perform(get("/section-enrollments/{id}/change-section-candidates", 999999999L)
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-60000023", "PRECEPTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void waitlistedStudentIsNotifiedWhenAWithdrawalFreesTheSeat() throws Exception {
        Institution institution = persistInstitution();
        Student seated = persistStudent("60000031", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Student waiting = persistStudent("60000032", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Subject subject = persistSubject("Materia60000031");
        Program program = persistProgram("Carrera60000031", 100);
        persistProgramSubject(program, subject, 10);
        SubjectSection section = persistSubjectSection(subject, persistTerm(null, null), 1);
        SubjectSectionEnrollment seatedEnrollment = persistSectionEnrollment(seated, persistProgramEnrollment(seated, program, ProgramEnrollmentStatus.ENROLLED), section, EnrollmentStatus.ENROLLED, 1);
        SubjectSectionEnrollment waitingEnrollment = persistSectionEnrollment(waiting, persistProgramEnrollment(waiting, program, ProgramEnrollmentStatus.ENROLLED), section, EnrollmentStatus.WAITLISTED, 1);
        entityManager.flush();

        withdrawSubject(seatedEnrollment.getId(), tokenWithRole("preceptor-60000031", "PRECEPTOR"), STAFF_REASON_BODY).andExpect(status().isNoContent());

        List<SubjectWaitlistPromotedEvent> promoted = events.stream(SubjectWaitlistPromotedEvent.class).toList();
        assertThat(promoted).singleElement().satisfies(event -> {
            assertThat(event.enrollmentId()).isEqualTo(waitingEnrollment.getId());
            assertThat(event.studentPersonId()).isEqualTo(waiting.getPerson().getId());
            assertThat(event.subjectName()).isEqualTo("Materia60000031");
        });
    }

    @Test
    void attendanceAndGradesStayInTheWithdrawnEnrollmentAndTheNewOneStartsEmpty() throws Exception {
        ChangeScenario scenario = changeScenario("60000041", null, LocalTime.of(10, 0), LocalTime.of(12, 0), "DIRECTOR");
        Instructor recorder = new Instructor();
        Person recorderPerson = new Person();
        recorderPerson.setName("Docente");
        recorderPerson.setLastname("Prueba");
        recorderPerson.setDni("60000042");
        recorderPerson.setUsername("docente-60000042");
        recorderPerson.setEmail("docente-60000042@example.com");
        Address address = new Address();
        entityManager.persist(address);
        recorderPerson.setAddress(address);
        recorderPerson.setStatus(StatusType.ACTIVE);
        entityManager.persist(recorderPerson);
        recorder.setPerson(recorderPerson);
        entityManager.persist(recorder);
        Attendance attendance = new Attendance();
        attendance.setProgramSubjectSectionEnrollment(scenario.origin());
        attendance.setClassDate(java.time.LocalDate.of(2026, 4, 6));
        attendance.setStatus(AttendanceStatus.PRESENT);
        attendance.setRecordedBy(recorder);
        entityManager.persist(attendance);
        GradeItem gradeItem = new GradeItem();
        gradeItem.setProgramSubjectSectionEnrollment(scenario.origin());
        gradeItem.setType(GradeItemType.TP);
        gradeItem.setSequenceNumber(1);
        entityManager.persist(gradeItem);
        entityManager.flush();
        Long originId = scenario.origin().getId();

        String body = changeSection(scenario, STAFF_REASON).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Long newId = objectMapper.readTree(body).get("id").asLong();
        assertThat(newId).isNotEqualTo(originId);
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.find(SubjectSectionEnrollment.class, originId).getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(countAttendance(originId)).isEqualTo(1L);
        assertThat(countGradeItems(originId)).isEqualTo(1L);
        assertThat(countAttendance(newId)).isZero();
        assertThat(countGradeItems(newId)).isZero();
    }

    private long countAttendance(Long enrollmentId) {
        return entityManager.createQuery("select count(a) from Attendance a where a.programSubjectSectionEnrollment.id = :id", Long.class)
                .setParameter("id", enrollmentId).getSingleResult();
    }

    private long countGradeItems(Long enrollmentId) {
        return entityManager.createQuery("select count(g) from GradeItem g where g.programSubjectSectionEnrollment.id = :id", Long.class)
                .setParameter("id", enrollmentId).getSingleResult();
    }

    @Test
    void courseProgressListsEachModuleEnrollmentWithItsIdAndStatus() throws Exception {
        Institution institution = persistInstitution();
        Student student = persistStudent("60000051", StudentOnboardingStatus.CONFIRMADO, StatusType.ACTIVE, institution);
        Term term = persistTerm(null, null);
        Course course = persistCourse(term, null, null);
        CourseSubject module = persistCourseSubject(course, "Modulo 60000051");
        CourseSubjectSection section = persistCourseSubjectSection(module, term, 10);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(courseEnrollment);
        CourseSubjectSectionEnrollment moduleEnrollment = new CourseSubjectSectionEnrollment();
        moduleEnrollment.setStudent(student);
        moduleEnrollment.setCourseEnrollment(courseEnrollment);
        moduleEnrollment.setCourseSubjectSection(section);
        moduleEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        moduleEnrollment.setEnrolledAt(LocalDateTime.now());
        moduleEnrollment.setAttemptNumber(1);
        entityManager.persist(moduleEnrollment);
        entityManager.flush();

        mockMvc.perform(get("/course-enrollments/{id}/progress", courseEnrollment.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-60000051", "PRECEPTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleEnrollments[0].sectionEnrollmentId").value(moduleEnrollment.getId()))
                .andExpect(jsonPath("$.moduleEnrollments[0].moduleId").value(module.getId()))
                .andExpect(jsonPath("$.moduleEnrollments[0].moduleName").value("Modulo 60000051"))
                .andExpect(jsonPath("$.moduleEnrollments[0].status").value("ENROLLED"))
                .andExpect(jsonPath("$.modulosPendientes[0]").value("Modulo 60000051"));
    }
}
