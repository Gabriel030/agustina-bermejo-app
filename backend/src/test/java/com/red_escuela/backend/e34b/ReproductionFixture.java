package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class ReproductionFixture extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(910_000_000);

    @Autowired
    protected EntityManager entityManager;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    protected Person person() {
        String dni = String.valueOf(SEQUENCE.incrementAndGet());
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    protected Student student() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    protected Program program(int totalCreditsRequired) {
        Program program = new Program();
        program.setName("Carrera " + SEQUENCE.incrementAndGet());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(totalCreditsRequired);
        entityManager.persist(program);
        return program;
    }

    protected Subject subject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    protected ProgramSubject programSubject(Program program, Subject subject, int credits, StatusType status) {
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(credits);
        programSubject.setCurricularYear(1);
        programSubject.setStatus(status);
        entityManager.persist(programSubject);
        return programSubject;
    }

    protected Term term(LocalDateTime start, LocalDateTime end) {
        Term term = TermFixtures.newTerm();
        term.setYear(start.getYear());
        term.setDescription("Termino " + SEQUENCE.incrementAndGet());
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(start);
        term.setEndDate(end);
        entityManager.persist(term);
        return term;
    }

    protected ProgramEnrollment programEnrollment(Student student, Program program, ProgramEnrollmentStatus status) {
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(status);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        if (status == ProgramEnrollmentStatus.WITHDRAWN) {
            programEnrollment.setWithdrawalEffectiveDate(java.time.LocalDate.of(2026, 5, 1));
        }
        if (status == ProgramEnrollmentStatus.COMPLETED) {
            programEnrollment.setCompletionDate(LocalDateTime.of(2026, 7, 1, 0, 0));
        }
        entityManager.persist(programEnrollment);
        return programEnrollment;
    }

    protected SubjectSection section(Subject subject, Term term) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    protected SubjectSectionEnrollment sectionEnrollment(ProgramEnrollment programEnrollment, SubjectSection section,
                                                         EnrollmentStatus status) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(programEnrollment.getStudent());
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
