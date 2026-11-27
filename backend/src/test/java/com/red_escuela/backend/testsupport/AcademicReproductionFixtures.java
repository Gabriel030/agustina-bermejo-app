package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AcademicReproductionFixtures {

    private final EntityManager entityManager;

    public AcademicReproductionFixtures(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Institution institution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        return institution;
    }

    private Person person(String name, String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName(name);
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    public Student student(String dni) {
        Student student = new Student();
        student.setPerson(person("Alumno", dni));
        student.setInstitution(institution());
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    public Instructor instructor(String dni) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person("Docente", dni));
        entityManager.persist(instructor);
        return instructor;
    }

    public EvaluationScheme scheme(int examCount, int tpCount, boolean hasFinal) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema de prueba");
        scheme.setExamCountExpected(examCount);
        scheme.setTpCountExpected(tpCount);
        scheme.setHasFinal(hasFinal);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        return scheme;
    }

    public Subject subject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        subject.setStatus(SubjectStatus.ACTIVE);
        entityManager.persist(subject);
        return subject;
    }

    public Term openTerm() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    public SubjectSection section(Subject subject, Term term, EvaluationScheme scheme) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setEvaluationScheme(scheme);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    public void assignInstructor(SubjectSection section, Instructor instructor) {
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
    }

    public Program program(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    public ProgramSubject programSubject(Program program, Subject subject, int year, CurricularTerm curricularTerm) {
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(year);
        programSubject.setCurricularTerm(curricularTerm);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return programSubject;
    }

    public ProgramEnrollment programEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    public SubjectSectionEnrollment sectionEnrollment(Student student, ProgramEnrollment programEnrollment,
                                                      SubjectSection section, EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    public SubjectSectionEnrollment enrollInSection(String dni, SubjectSection section) {
        Student student = student(dni);
        return sectionEnrollment(student,
                programEnrollment(student, program("Carrera " + dni), ProgramEnrollmentStatus.ENROLLED),
                section, EnrollmentStatus.IN_PROGRESS);
    }

    public CommissionClosure closure(SectionKind kind, Long sectionId, ClosureStatus status, Long closedByPersonId) {
        CommissionClosure closure = CommissionClosure.builder().sectionKind(kind).sectionId(sectionId).status(status)
                .reportNumber("ACTA-1").book("Libro 1").folio("1").closedById(closedByPersonId).closedAt(LocalDateTime.now()).build();
        entityManager.persist(closure);
        entityManager.flush();
        return closure;
    }
}
