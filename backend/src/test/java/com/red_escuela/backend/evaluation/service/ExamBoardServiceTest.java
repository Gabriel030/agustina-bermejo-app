package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;

import com.red_escuela.backend.academicstructure.entity.Holiday;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.evaluation.dto.CreateExamBoardRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardRegistrationSummaryResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotYetTakenException;
import com.red_escuela.backend.evaluation.exception.ExamDateOnHolidayException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamBoardServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ExamBoardService examBoardService;

    @Autowired
    private EntityManager entityManager;

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private ExamBoard persistExamBoard(Subject subject, Term term, LocalDateTime examDate) {
        ExamBoard examBoard = new ExamBoard();
        examBoard.setSubject(subject);
        examBoard.setTerm(term);
        examBoard.setExamDate(examDate);
        examBoard.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(examBoard);
        return examBoard;
    }

    private SubjectSectionEnrollment persistRegularEnrollment(Subject subject, Term term, String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = new Program();
        program.setName("Carrera mesas " + dni);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.REGULAR);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    @Test
    void creatingAnExamBoardOnAHolidayFails() {
        Holiday holiday = new Holiday();
        holiday.setDate(LocalDate.of(2099, 11, 20));
        holiday.setDescription("Feriado de prueba");
        entityManager.persist(holiday);
        Subject subject = persistSubject("MateriaMesaEnFeriado");
        Term term = persistTerm();
        entityManager.flush();

        assertThatThrownBy(() -> examBoardService.create(new CreateExamBoardRequest(
                subject.getId(), null, term.getId(), LocalDateTime.of(2099, 11, 20, 18, 0), null, null, null)))
                .isInstanceOf(ExamDateOnHolidayException.class);
    }

    @Test
    void closingAnExamBoardBeforeItsExamDateFails() {
        Subject subject = persistSubject("MateriaMesaFutura");
        ExamBoard examBoard = persistExamBoard(subject, persistTerm(), LocalDateTime.of(2099, 11, 23, 18, 0));
        entityManager.flush();

        assertThatThrownBy(() -> examBoardService.close(examBoard.getId()))
                .isInstanceOf(ExamBoardNotYetTakenException.class);
    }

    @Test
    void closingAnExamBoardAfterItsExamDateSucceeds() {
        Subject subject = persistSubject("MateriaMesaRendida");
        ExamBoard examBoard = persistExamBoard(subject, persistTerm(), LocalDateTime.of(2020, 9, 2, 18, 0));
        entityManager.flush();

        assertThat(examBoardService.close(examBoard.getId()).getStatus()).isEqualTo(ExamBoardStatus.CLOSED);
    }

    @Test
    void registrantsSummaryIncludesTheFinalGradeOfEachGradedStudent() {
        Subject subject = persistSubject("MateriaMesaConNotas");
        Term term = persistTerm();
        ExamBoard examBoard = persistExamBoard(subject, term, LocalDateTime.of(2020, 9, 2, 18, 0));
        SubjectSectionEnrollment enrollment = persistRegularEnrollment(subject, term, "72000001");
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(examBoard);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.GRADED);
        registration.setRegisteredAt(LocalDateTime.of(2020, 8, 20, 10, 0));
        entityManager.persist(registration);
        GradeItem finalGrade = new GradeItem();
        finalGrade.setProgramSubjectSectionEnrollment(enrollment);
        finalGrade.setType(GradeItemType.FINAL);
        finalGrade.setSequenceNumber(1);
        finalGrade.setDate(LocalDateTime.of(2020, 9, 2, 18, 0));
        finalGrade.setScore(Grade.of(BigDecimal.valueOf(7.0)));
        finalGrade.setExamBoard(examBoard);
        entityManager.persist(finalGrade);
        entityManager.flush();

        List<ExamBoardRegistrationSummaryResponse> summary = examBoardService.listRegistrantsWithDetail(examBoard.getId());

        assertThat(summary).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("GRADED");
            assertThat(row.finalScore()).isEqualByComparingTo(BigDecimal.valueOf(7.0));
        });
    }
}
