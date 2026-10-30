package com.red_escuela.backend.characterization.coursework;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.dto.CurricularTerm;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.dto.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class CourseworkCharacterizationSupport extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected JwtService jwtService;

    protected record Classroom(
            Person teacher, Instructor instructor, Person student, SubjectSection section,
            SubjectSectionEnrollment enrollment, ProgramEnrollment programEnrollment, Subject subject) {
    }

    protected Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Nombre");
        person.setLastname("Apellido " + dni);
        person.setUsername(username);
        person.setDni(dni);
        person.setEmail(username + "@example.com");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    protected Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    protected Student persistStudent(Person person) {
        Institution institution = new Institution();
        institution.setName("Instituto " + person.getDni());
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    protected EvaluationScheme persistScheme(int examCount, boolean hasFinal, Double promotionMinGrade) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + TestSubjectCodes.next());
        scheme.setExamCountExpected(examCount);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(hasFinal);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        scheme.setPromotionMinGrade(promotionMinGrade == null ? null : Grade.of(BigDecimal.valueOf(promotionMinGrade)));
        entityManager.persist(scheme);
        return scheme;
    }

    protected Term persistTerm(LocalDateTime startDate, LocalDateTime endDate) {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(startDate);
        term.setEndDate(endDate);
        entityManager.persist(term);
        return term;
    }

    protected Program persistProgram(String name, int totalCredits) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(totalCredits);
        entityManager.persist(program);
        return program;
    }

    protected Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    protected ProgramSubject persistPlanEntry(Program program, Subject subject, int credits) {
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setSubject(subject);
        entry.setCredits(credits);
        entry.setCurricularYear(1);
        entry.setCurricularTerm(CurricularTerm.FIRST);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        return entry;
    }

    protected Classroom persistClassroom(String key, String dniBase, EvaluationScheme scheme, Percentage maxAbsenceRate,
                                         int programCredits, int subjectCredits, InstructorRole teacherRole) {
        Person teacher = persistPerson(key + ".teacher", dniBase + "1", "DOCENTE");
        Instructor instructor = persistInstructor(teacher);
        Person studentPerson = persistPerson(key + ".student", dniBase + "2", "ALUMNO");
        Student student = persistStudent(studentPerson);
        Subject subject = persistSubject("Materia " + key);
        Program program = persistProgram("Carrera " + key, programCredits);
        persistPlanEntry(program, subject, subjectCredits);
        Term term = persistTerm(null, null);

        SubjectSection section = new SubjectSection();
        section.setName("Comision " + key);
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setMaxAbsenceRate(maxAbsenceRate);
        section.setCapacity(30);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);

        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(teacherRole);
        entityManager.persist(assignment);

        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);

        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
        return new Classroom(teacher, instructor, studentPerson, section, enrollment, programEnrollment, subject);
    }

    protected String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    protected ResultActions postJson(Person actor, String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions postEmpty(Person actor, String path) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(actor)));
    }

    protected ResultActions putJson(Person actor, String path, Object body) throws Exception {
        return mockMvc.perform(put(path)
                .header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions getAs(Person actor, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(actor)));
    }

    protected JsonNode bodyOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected EnrollmentStatus statusOf(SubjectSectionEnrollment enrollment) {
        entityManager.flush();
        entityManager.refresh(enrollment);
        return enrollment.getStatus();
    }

    protected long countRows(String entityName, String where) {
        entityManager.flush();
        return entityManager.createQuery("select count(e) from " + entityName + " e where " + where, Long.class)
                .getSingleResult();
    }
}
