package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.RescheduleExamBoardRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.specialist.ExamBoardRegistrationManager;
import com.red_escuela.backend.evaluation.specialist.ExamBoardRescheduler;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentSectionWithdrawer;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamBoardRulesReproductionIT extends ReproductionFixture {

    private static final LocalDateTime FUTURE_EXAM = LocalDateTime.of(2030, 3, 4, 10, 0);

    @Autowired
    private ExamBoardRegistrationManager registrationManager;
    @Autowired
    private ExamBoardRescheduler rescheduler;
    @Autowired
    private com.red_escuela.backend.evaluation.specialist.ExamBoardManager examBoardManager;
    @Autowired
    private ProgramEnrollmentSectionWithdrawer sectionWithdrawer;
    @Autowired
    private SubjectSectionEnrollmentRepository sectionEnrollmentRepository;

    private ExamBoard examBoard(Subject subject, Term term, LocalDateTime examDate) {
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(examDate);
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        addTribunal(board);
        return board;
    }

    private void addTribunal(ExamBoard board) {
        Instructor instructor = new Instructor();
        instructor.setPerson(person());
        entityManager.persist(instructor);
        ExamBoardInstructor assignment = new ExamBoardInstructor();
        assignment.setExamBoard(board);
        assignment.setInstructor(instructor);
        entityManager.persist(assignment);
        board.setTribunal(new java.util.ArrayList<>(java.util.List.of(assignment)));
    }

    private SubjectSectionEnrollment regularEnrollmentIn(Term term, Subject subject, ProgramEnrollmentStatus programStatus) {
        Program program = program(4);
        ProgramEnrollment programEnrollment = programEnrollment(student(), program, programStatus);
        return sectionEnrollment(programEnrollment, section(subject, term), EnrollmentStatus.REGULAR);
    }

    @Test
    void examBoardRegistrationRequiresAnActiveProgramEnrollment() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = examBoard(subject, term, FUTURE_EXAM);
        SubjectSectionEnrollment active = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment withdrawn = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.WITHDRAWN);
        flushAndClear();

        assertThatCode(() -> registrationManager.registerProgramEnrollment(board.getId(), active.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(board.getId(), withdrawn.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void registrationAfterTheExamDateIsRejectedEvenWithoutRegistrationEnd() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard futureBoard = examBoard(subject, term, FUTURE_EXAM);
        ExamBoard pastBoard = examBoard(subject, term, LocalDateTime.of(2026, 6, 1, 10, 0));
        SubjectSectionEnrollment control = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment late = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        flushAndClear();

        assertThatCode(() -> registrationManager.registerProgramEnrollment(futureBoard.getId(), control.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(pastBoard.getId(), late.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void examBoardCannotBeRescheduledToAPastDate() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = examBoard(subject, term, FUTURE_EXAM);
        flushAndClear();

        assertThatThrownBy(() -> rescheduler.reschedule(board.getId(),
                new RescheduleExamBoardRequest(LocalDateTime.of(2020, 3, 2, 10, 0), null, null, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void examBoardCannotBeRescheduledToAWeekend() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = examBoard(subject, term, FUTURE_EXAM);
        flushAndClear();

        assertThatThrownBy(() -> rescheduler.reschedule(board.getId(),
                new RescheduleExamBoardRequest(LocalDateTime.of(2030, 3, 2, 10, 0), null, null, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void withdrawingTheProgramEnrollmentAlsoClosesRegularSectionEnrollments() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        SubjectSectionEnrollment regular = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        flushAndClear();

        sectionWithdrawer.withdrawOpenEnrollmentsOf(regular.getProgramEnrollment().getId());
        flushAndClear();

        assertThat(sectionEnrollmentRepository.findById(regular.getId()).orElseThrow().getStatus())
                .isNotEqualTo(EnrollmentStatus.REGULAR);
    }

    @Test
    void closingAnExamBoardWithoutTribunalIsRejected() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2019, 3, 1, 0, 0), LocalDateTime.of(2019, 7, 1, 0, 0));
        ExamBoard withoutTribunal = new ExamBoard();
        withoutTribunal.setSubject(subject);
        withoutTribunal.setTerm(term);
        withoutTribunal.setExamDate(LocalDateTime.of(2019, 6, 3, 10, 0));
        withoutTribunal.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(withoutTribunal);
        ExamBoard withTribunal = examBoard(subject, term, LocalDateTime.of(2019, 6, 4, 10, 0));
        flushAndClear();

        assertThatThrownBy(() -> examBoardManager.close(withoutTribunal.getId()))
                .isInstanceOf(com.red_escuela.backend.evaluation.exception.ExamBoardWithoutTribunalException.class);
        assertThatCode(() -> examBoardManager.close(withTribunal.getId())).doesNotThrowAnyException();
    }

    private DirectorApprovalRequest approvedLateExam(SubjectSectionEnrollment enrollment, Subject subject) {
        ProgramEnrollment programEnrollment = entityManager.find(ProgramEnrollment.class, enrollment.getProgramEnrollment().getId());
        DirectorApprovalRequest request = DirectorApprovalRequest.open(ExceptionType.LATE_EXAM, programEnrollment, subject,
                "Motivo", person(), LocalDateTime.of(2026, 1, 1, 10, 0));
        request.decide(DirectorApprovalRequestStatus.APPROVED, person(), "Ok", LocalDateTime.of(2026, 1, 2, 10, 0));
        entityManager.persist(request);
        return request;
    }

    private ExamBoard boardWithClosedRegistrationWindow(Subject subject, Term term) {
        ExamBoard board = examBoard(subject, term, FUTURE_EXAM);
        board.setRegistrationEnd(LocalDateTime.of(2026, 1, 1, 0, 0));
        return board;
    }

    private Object consumedAtOf(Long requestId) {
        return entityManager.createNativeQuery("select consumed_at from director_approval_request where id = :id")
                .setParameter("id", requestId).getSingleResult();
    }

    @Test
    void lateExamApprovalIsConsumedByTheRegistrationThatUsesIt() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = boardWithClosedRegistrationWindow(subject, term);
        SubjectSectionEnrollment enrollment = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        DirectorApprovalRequest approval = approvedLateExam(enrollment, subject);
        flushAndClear();

        registrationManager.registerProgramEnrollment(board.getId(), enrollment.getId());
        flushAndClear();

        assertThat(consumedAtOf(approval.getId())).isNotNull();
    }

    @Test
    void rejectedRegistrationDoesNotConsumeTheLateExamApproval() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = boardWithClosedRegistrationWindow(subject, term);
        Program program = program(4);
        ProgramEnrollment programEnrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment notRegular = sectionEnrollment(programEnrollment, section(subject, term), EnrollmentStatus.IN_PROGRESS);
        DirectorApprovalRequest approval = approvedLateExam(notRegular, subject);
        flushAndClear();

        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(board.getId(), notRegular.getId()))
                .isInstanceOf(RuntimeException.class);

        assertThat(consumedAtOf(approval.getId())).isNull();
    }

    @Test
    void consumedLateExamApprovalDoesNotOpenTheClosedWindowAgain() {
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard board = boardWithClosedRegistrationWindow(subject, term);
        ExamBoard otherBoard = boardWithClosedRegistrationWindow(subject, term);
        otherBoard.setExamDate(FUTURE_EXAM.plusDays(1));
        SubjectSectionEnrollment enrollment = regularEnrollmentIn(term, subject, ProgramEnrollmentStatus.ENROLLED);
        approvedLateExam(enrollment, subject);
        flushAndClear();
        ExamBoardRegistration registration = registrationManager.registerProgramEnrollment(board.getId(), enrollment.getId());
        registrationManager.withdraw(registration.getId());
        flushAndClear();

        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(otherBoard.getId(), enrollment.getId()))
                .isInstanceOf(com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException.class);
    }

    @Test
    void sameStudentCannotRegisterInTwoBoardsAtTheSameDateTime() {
        Subject algebra = subject("Algebra");
        Subject physics = subject("Fisica");
        Term term = term(LocalDateTime.of(2029, 3, 1, 0, 0), LocalDateTime.of(2029, 7, 1, 0, 0));
        ExamBoard algebraBoard = examBoard(algebra, term, FUTURE_EXAM);
        ExamBoard physicsBoard = examBoard(physics, term, FUTURE_EXAM);
        ExamBoard physicsOtherDayBoard = examBoard(physics, term, FUTURE_EXAM.plusDays(1));
        ProgramEnrollment programEnrollment = programEnrollment(student(), program(4), ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment inAlgebra = sectionEnrollment(programEnrollment, section(algebra, term), EnrollmentStatus.REGULAR);
        SubjectSectionEnrollment inPhysics = sectionEnrollment(programEnrollment, section(physics, term), EnrollmentStatus.REGULAR);
        flushAndClear();

        registrationManager.registerProgramEnrollment(algebraBoard.getId(), inAlgebra.getId());
        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(physicsBoard.getId(), inPhysics.getId()))
                .isInstanceOf(com.red_escuela.backend.evaluation.exception.StudentExamBoardOverlapException.class);
        assertThatCode(() -> registrationManager.registerProgramEnrollment(physicsOtherDayBoard.getId(), inPhysics.getId()))
                .doesNotThrowAnyException();
    }
}
