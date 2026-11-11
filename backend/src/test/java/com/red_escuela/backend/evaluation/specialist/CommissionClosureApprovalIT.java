package com.red_escuela.backend.evaluation.specialist;

import tools.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.NotOwnSectionException;
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
import com.red_escuela.backend.evaluation.domain.ClosureBlockedReason;
import com.red_escuela.backend.evaluation.domain.ClosureSectionKind;
import com.red_escuela.backend.evaluation.domain.ClosureViewerProfile;
import com.red_escuela.backend.evaluation.dto.ClosureSheetResponse;
import com.red_escuela.backend.evaluation.entity.ClosureAuditAction;
import com.red_escuela.backend.evaluation.entity.CommissionClosureAudit;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.evaluation.exception.*;
import com.red_escuela.backend.evaluation.repository.CommissionClosureAuditRepository;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.shared.time.MutableClock;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.CommissionClosure;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import com.red_escuela.backend.evaluation.exception.ClosureAlreadyConfirmedException;
import com.red_escuela.backend.evaluation.exception.ClosureNotAvailableException;
import com.red_escuela.backend.evaluation.exception.PublishedGradesIncompleteException;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureStudentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommissionClosureApprovalIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CommissionClosureSheetReader sheetReader;
    @Autowired
    private CommissionClosureTeacherConfirmer teacherConfirmer;
    @Autowired
    private CommissionClosureApprover approver;
    @Autowired
    private CommissionClosureReopener reopener;
    @Autowired
    private ClosureExceptionManager exceptionManager;
    @Autowired
    private CommissionClosureRepository closureRepository;
    @Autowired
    private CommissionClosureStudentRepository closureStudentRepository;
    @Autowired
    private CommissionClosureAuditRepository auditRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int sequence = 83000000;
    private Institution institution;
    private Long teacherPersonId;
    private Long teacherInstructorId;
    private Long strangerPersonId;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private SubjectSection persistCommission() {
        institution = new Institution();
        institution.setName("Sheet institute");
        entityManager.persist(institution);
        Program program = new Program();
        program.setName("Sheet program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Sheet subject");
        entityManager.persist(subject);
        ProgramSubject entry = new ProgramSubject();
        entry.setProgram(program);
        entry.setSubject(subject);
        entry.setCredits(4);
        entry.setStatus(StatusType.ACTIVE);
        entityManager.persist(entry);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-2C");
        term.setEndDate(LocalDateTime.of(2026, 12, 15, 23, 59));
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Scheme " + TestSubjectCodes.next());
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6)));
        scheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7)));
        entityManager.persist(scheme);
        SubjectSection section = new SubjectSection();
        section.setName("Sheet section");
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        section.setEvaluationScheme(scheme);
        entityManager.persist(section);
        Person teacherPerson = persistPerson();
        teacherPersonId = teacherPerson.getId();
        Instructor teacher = persistInstructor(teacherPerson);
        teacherInstructorId = teacher.getId();
        strangerPersonId = persistPerson().getId();
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(teacher);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
        int[][] scores = {{8, 9}, {8, 9}, {5, 5}, {2, 2}, {8, 9}};
        for (int index = 0; index < scores.length; index++) {
            Student student = persistStudent();
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
            enrollment.setStatus(index == 4 ? EnrollmentStatus.WITHDRAWN : EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);
            persistExam(enrollment, 1, scores[index][0]);
            persistExam(enrollment, 2, scores[index][1]);
            enrollments.add(enrollment.getId());
        }
        entityManager.flush();
        entityManager.clear();
        return section;
    }

    private void publishExam(Long sectionId, int sequenceNumber) {
        entityManager.persist(SectionGradeItemPublication.of(
                SectionKind.PROGRAM, sectionId, GradeItemType.EXAM, sequenceNumber, LocalDateTime.now(), null));
        entityManager.flush();
        entityManager.clear();
    }

    private void persistExam(SubjectSectionEnrollment enrollment, int sequenceNumber, int score) {
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(sequenceNumber);
        item.setWeight(0.5);
        item.setDate(LocalDateTime.now());
        item.setScore(Grade.of(BigDecimal.valueOf(score)));
        entityManager.persist(item);
    }

    private Person persistPerson() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Instructor persistInstructor(Person person) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person);
        entityManager.persist(instructor);
        return instructor;
    }

    private Student persistStudent() {
        Student student = new Student();
        student.setPerson(persistPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private void actAsTeacher() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(teacherPersonId), null, List.of()));
    }


    private List<Long> enrollments = new ArrayList<>();
    private Long sectionId;

    private void confirmedSection() {
        SubjectSection section = persistCommission();
        sectionId = section.getId();
        publishExam(sectionId, 1);
        publishExam(sectionId, 2);
        sheetReader.readSheet(ClosureSectionKind.PROGRAM, sectionId, ClosureViewerProfile.STAFF, null);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        actAs(teacherPersonId);
        teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, sectionId, teacherInstructorId);
        flushAndClear();
    }

    private void actAs(Long personId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(personId), null, List.of()));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private SubjectSectionEnrollment enrollment(int index) {
        return entityManager.find(SubjectSectionEnrollment.class, enrollments.get(index));
    }

    private CommissionClosure closure() {
        return closureRepository.findBySectionKindAndSectionId(SectionKind.PROGRAM, sectionId).orElseThrow();
    }

    @Test
    void approvingAssignsReportNumberBookAndFolioAndAppliesTheResultsToTheEnrollments() {
        confirmedSection();
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "Libro 3", "Folio 12", false);
        flushAndClear();

        CommissionClosure closed = closure();
        assertThat(closed.getStatus()).isEqualTo(ClosureStatus.CLOSED);
        assertThat(closed.getReportNumber()).isEqualTo("ACTA-" + LocalDate.of(2026, 12, 20).getYear() + "-0001");
        assertThat(closed.getBook()).isEqualTo("Libro 3");
        assertThat(closed.getFolio()).isEqualTo("Folio 12");
        assertThat(closed.isClosedWithoutTeacherConfirmation()).isFalse();
        assertThat(auditRepository.findAll()).extracting(CommissionClosureAudit::getAction)
                .contains(ClosureAuditAction.CLOSED);
        assertThat(enrollment(0).getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(enrollment(0).getFinalGradeType()).isNull();
        assertThat(enrollment(3).getStatus()).isEqualTo(EnrollmentStatus.FAILED);
        assertThat(closureStudentRepository.findByClosureId(closed.getId()))
                .extracting(row -> row.getFinalCondition())
                .doesNotContain(ClosureFinalCondition.PROMOTION_CONDITIONAL);
    }

    @Test
    void aConditionalPromotionWithAnExceptionBecomesApprovedWithItsDecimalPromotionGrade() {
        confirmedSection();
        actAs(directorPersonId());
        exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0), "Pago en trámite acordado");
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "Libro 3", "Folio 12", false);
        flushAndClear();

        assertThat(enrollment(0).getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollment(0).getFinalGradeType()).isEqualTo(FinalGradeType.PROMOTION);
        assertThat(enrollment(0).getFinalScore()).isEqualTo(Grade.of(new BigDecimal("8.50")));
        assertThat(enrollment(1).getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(enrollment(1).getFinalScore()).isNull();
    }

    @Test
    void aStudentWhoWithdrewAfterTheTeacherConfirmationKeepsTheWithdrawnStatus() {
        confirmedSection();
        enrollment(0).setStatus(EnrollmentStatus.WITHDRAWN);
        flushAndClear();

        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", false);
        flushAndClear();

        assertThat(enrollment(0).getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(enrollment(2).getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void approvingWithoutTeacherConfirmationRequiresTheFlagAndLeavesAnAuditTrail() {
        SubjectSection section = persistCommission();
        sectionId = section.getId();
        publishExam(sectionId, 1);
        publishExam(sectionId, 2);
        sheetReader.readSheet(ClosureSectionKind.PROGRAM, sectionId, ClosureViewerProfile.STAFF, null);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 20));
        actAs(teacherPersonId);

        assertThatThrownBy(() -> approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", false))
                .isInstanceOf(ClosureNotConfirmedException.class);
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", true);
        flushAndClear();

        assertThat(closure().isClosedWithoutTeacherConfirmation()).isTrue();
        assertThat(auditRepository.findAll()).extracting(CommissionClosureAudit::getAction)
                .contains(ClosureAuditAction.CLOSED_WITHOUT_TEACHER_CONFIRMATION);
        assertThatThrownBy(() -> approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", true))
                .isInstanceOf(ClosureAlreadyClosedException.class);
    }

    @Test
    void approvingBeforeTheTermEndIsRejectedAndTheDayAfterItIsAccepted() {
        confirmedSection();
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 14));

        assertThatThrownBy(() -> approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", false))
                .isInstanceOf(ClosureBeforeTermEndException.class);
        MutableClock.INSTANCE.setDate(LocalDate.of(2026, 12, 15));
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", false);
        flushAndClear();

        assertThat(closure().getStatus()).isEqualTo(ClosureStatus.CLOSED);
    }

    @Test
    void reportNumbersRestartEachYear() {
        ClosureReportNumberAllocator in2026 = new ClosureReportNumberAllocator(jdbcTemplate, fixedClock(2031, 3, 1));
        ClosureReportNumberAllocator in2027 = new ClosureReportNumberAllocator(jdbcTemplate, fixedClock(2032, 3, 1));

        assertThat(List.of(in2026.allocateNextReportNumber(), in2026.allocateNextReportNumber(),
                in2027.allocateNextReportNumber(), in2026.allocateNextReportNumber()))
                .containsExactly("ACTA-2031-0001", "ACTA-2031-0002", "ACTA-2032-0001", "ACTA-2031-0003");
    }

    @Test
    void reopeningRequiresAReasonKeepsTheNumberAndBumpsTheVersionUntilItIsClosedAgain() {
        confirmedSection();
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "Libro 3", "Folio 12", false);
        flushAndClear();
        String number = closure().getReportNumber();
        actAs(directorPersonId());

        assertThatThrownBy(() -> reopener.reopenClosure(ClosureSectionKind.PROGRAM, sectionId, "corto"))
                .isInstanceOf(InvalidClosureReopenReasonException.class);
        reopener.reopenClosure(ClosureSectionKind.PROGRAM, sectionId, "Se corrigió una nota del segundo parcial");
        flushAndClear();

        CommissionClosure reopened = closure();
        assertThat(reopened.getStatus()).isEqualTo(ClosureStatus.REOPENED);
        assertThat(reopened.getReportVersion()).isEqualTo(2);
        assertThat(reopened.getReportNumber()).isEqualTo(number);
        assertThat(auditRepository.findAll()).anySatisfy(audit -> {
            assertThat(audit.getAction()).isEqualTo(ClosureAuditAction.REOPENED);
            assertThat(audit.getReason()).isEqualTo("Se corrigió una nota del segundo parcial");
            assertThat(audit.getActorPersonId()).isEqualTo(directorPersonId());
        });
        assertThatThrownBy(() -> reopener.reopenClosure(ClosureSectionKind.PROGRAM, sectionId, "Segundo intento de reabrir"))
                .isInstanceOf(ClosureNotReopenableException.class);
        actAs(teacherPersonId);
        teacherConfirmer.confirmClosure(ClosureSectionKind.PROGRAM, sectionId, teacherInstructorId);
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "Libro 3", "Folio 13", false);
        flushAndClear();
        assertThat(closure().getReportNumber()).isEqualTo(number);
        assertThat(jdbcTemplate.queryForObject("select last_number from closure_report_number_counter where report_year = 2026", Integer.class))
                .isEqualTo(1);
        assertThat(closure().getReportVersion()).isEqualTo(2);
        assertThat(closure().getStatus()).isEqualTo(ClosureStatus.CLOSED);
    }

    @Test
    void exceptionsNeedAReasonAStudentOfTheSectionAndAnOpenClosureAndCanBeRevoked() {
        confirmedSection();
        actAs(directorPersonId());

        assertThatThrownBy(() -> exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0), "corto"))
                .isInstanceOf(InvalidClosureExceptionReasonException.class);
        assertThatThrownBy(() -> exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, -1L, "Motivo suficiente"))
                .isInstanceOf(ClosureStudentNotFoundException.class);
        exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0), "Motivo suficiente");
        assertThatThrownBy(() -> exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0), "Motivo suficiente"))
                .isInstanceOf(ClosureExceptionAlreadyGrantedException.class);
        ClosureSheetResponse withException = sheetReader.readSheet(ClosureSectionKind.PROGRAM, sectionId, ClosureViewerProfile.STAFF, null);
        exceptionManager.revokeException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0));
        assertThatThrownBy(() -> exceptionManager.revokeException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(0)))
                .isInstanceOf(ClosureExceptionNotFoundException.class);
        approver.approveClosure(ClosureSectionKind.PROGRAM, sectionId, "L", "F", false);
        flushAndClear();

        assertThat(withException.students()).filteredOn(row -> row.enrollmentId().equals(enrollments.get(0)))
                .singleElement().satisfies(row -> assertThat(row.promotionExempted()).isTrue());
        assertThat(enrollment(0).getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
        assertThat(auditRepository.findAll()).extracting(CommissionClosureAudit::getAction)
                .contains(ClosureAuditAction.EXCEPTION_GRANTED, ClosureAuditAction.EXCEPTION_REVOKED);
        assertThatThrownBy(() -> exceptionManager.grantException(ClosureSectionKind.PROGRAM, sectionId, enrollments.get(1), "Motivo suficiente"))
                .isInstanceOf(ClosureAlreadyClosedException.class);
    }

    @Test
    void withdrawnStudentsAreListedAsWithdrawnRowsAndDoNotCountInTheSummary() {
        SubjectSection section = persistCommission();
        publishExam(section.getId(), 1);
        publishExam(section.getId(), 2);

        ClosureSheetResponse sheet = sheetReader.readSheet(ClosureSectionKind.PROGRAM, section.getId(), ClosureViewerProfile.STAFF, null);

        assertThat(sheet.students()).hasSize(5);
        assertThat(sheet.students()).filteredOn(row -> row.enrollmentStatus() == EnrollmentStatus.WITHDRAWN)
                .singleElement().satisfies(row -> assertThat(row.enrollmentId()).isEqualTo(enrollments.get(4)));
        int counted = sheet.summary().promote() + sheet.summary().goToFinal() + sheet.summary().failed() + sheet.summary().free()
                + sheet.summary().pending() + sheet.summary().conditional();
        assertThat(counted).isEqualTo(4);
    }

    private Long directorPersonId() {
        return strangerPersonId;
    }

    private Clock fixedClock(int year, int month, int day) {
        return Clock.fixed(LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
