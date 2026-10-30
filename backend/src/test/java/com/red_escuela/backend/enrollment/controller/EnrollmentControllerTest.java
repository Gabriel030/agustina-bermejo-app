package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.shared.domain.Address;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
class EnrollmentControllerTest extends AbstractIntegrationTest {
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
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(enrollmentStart);
        term.setEnrollmentEnd(enrollmentEnd);
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
                        .content(objectMapper.writeValueAsString(new ChangeSectionRequest(seccionB.getId()))))
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
                        .content(objectMapper.writeValueAsString(new ChangeSectionRequest(section.getId()))))
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
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        CourseSubjectSectionEnrollment promovida = entityManager.find(CourseSubjectSectionEnrollment.class, subDeEstudiante2.getId());
        assertThat(promovida.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }
}
