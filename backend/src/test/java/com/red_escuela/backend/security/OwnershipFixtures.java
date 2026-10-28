package com.red_escuela.backend.security;

import com.red_escuela.backend.academicstructure.entity.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

final class OwnershipFixtures {

    private final EntityManager entityManager;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    private String ownerStudentToken;
    private String strangerStudentToken;
    private String instructorToken;
    private String strangerInstructorToken;
    private Long programEnrollmentId;
    private Long sectionEnrollmentId;
    private Long sectionId;

    OwnershipFixtures(TransactionTemplate transactions, EntityManager entityManager, RoleRepository roleRepository,
                      JwtService jwtService) {
        this.entityManager = entityManager;
        this.roleRepository = roleRepository;
        this.jwtService = jwtService;
        transactions.executeWithoutResult(status -> persistScenario());
    }

    Long resourceIdFor(OwnershipKind kind) {
        return switch (kind) {
            case PROGRAM_ENROLLMENT -> programEnrollmentId;
            case SECTION_ENROLLMENT -> sectionEnrollmentId;
            case SECTION_INSTRUCTOR -> sectionId;
        };
    }

    String ownerTokenFor(OwnershipKind kind) {
        return kind.ownerRole() == MatrixRole.ALUMNO ? ownerStudentToken : instructorToken;
    }

    String strangerTokenFor(OwnershipKind kind) {
        return kind.ownerRole() == MatrixRole.ALUMNO ? strangerStudentToken : strangerInstructorToken;
    }

    private void persistScenario() {
        Person ownerPerson = persistPerson("owner.student", "91000001", "ALUMNO");
        Person strangerPerson = persistPerson("stranger.student", "91000002", "ALUMNO");
        Person instructorPerson = persistPerson("owner.teacher", "91000003", "DOCENTE");
        Person strangerInstructorPerson = persistPerson("stranger.teacher", "91000004", "DOCENTE");
        Student ownerStudent = persistStudent(ownerPerson);
        persistStudent(strangerPerson);
        Instructor instructor = persistInstructor(instructorPerson);
        persistInstructor(strangerInstructorPerson);

        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia matriz");
        entityManager.persist(subject);
        Program program = new Program();
        program.setName("Carrera matriz");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramSubject planEntry = new ProgramSubject();
        planEntry.setProgram(program);
        planEntry.setSubject(subject);
        planEntry.setCredits(10);
        planEntry.setCurricularYear(1);
        planEntry.setCurricularTerm(CurricularTerm.FIRST);
        planEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(planEntry);
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(false);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);

        SubjectSection section = new SubjectSection();
        section.setName("Comision matriz");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setCapacity(30);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);

        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(ownerStudent);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(ownerStudent);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();

        ownerStudentToken = jwtService.generateAccessToken(ownerPerson);
        strangerStudentToken = jwtService.generateAccessToken(strangerPerson);
        instructorToken = jwtService.generateAccessToken(instructorPerson);
        strangerInstructorToken = jwtService.generateAccessToken(strangerInstructorPerson);
        programEnrollmentId = programEnrollment.getId();
        sectionEnrollmentId = enrollment.getId();
        sectionId = section.getId();
    }

    private Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Matriz");
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

    private Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private Student persistStudent(Person person) {
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
}
