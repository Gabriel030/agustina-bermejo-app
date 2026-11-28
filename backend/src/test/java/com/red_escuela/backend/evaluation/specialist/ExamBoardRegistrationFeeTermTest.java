package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeRequirementPort;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeRequirementPurpose;
import com.red_escuela.backend.enrollment.api.LateExamApprovalChecker;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationAfterExamDateException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWindowClosedException;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamBoardRegistrationFeeTermTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDateTime NOW = LocalDateTime.of(2029, 5, 1, 10, 0);

    private final ExamBoardRepository examBoardRepository = mock(ExamBoardRepository.class);
    private final SectionEnrollmentFinder sectionEnrollmentFinder = mock(SectionEnrollmentFinder.class);
    private final LateExamApprovalChecker lateExamApprovalChecker = mock(LateExamApprovalChecker.class);
    private final EnrollmentFeeRequirementPort feePort = mock(EnrollmentFeeRequirementPort.class);
    private final FinalExamEligibilityEvaluator eligibilityEvaluator = mock(FinalExamEligibilityEvaluator.class);
    private final ExamBoardRegistrationRepository registrationRepository = mock(ExamBoardRegistrationRepository.class);
    private ExamBoardRegistrationManager manager;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        manager = new ExamBoardRegistrationManager(registrationRepository, examBoardRepository, sectionEnrollmentFinder,
                lateExamApprovalChecker, eligibilityEvaluator, mock(FinalExamPrerequisiteChecker.class), feePort,
                mock(GradeItemRepository.class), clock);
    }

    private ExamBoard board(Term term, LocalDateTime examDate) {
        ExamBoard board = new ExamBoard();
        board.setId(10L);
        Subject subject = new Subject();
        subject.setId(3L);
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(examDate);
        board.setStatus(ExamBoardStatus.SCHEDULED);
        board.setTribunal(List.of(new ExamBoardInstructor()));
        when(examBoardRepository.findById(10L)).thenReturn(java.util.Optional.of(board));
        return board;
    }

    private SubjectSectionEnrollment enrollmentInTerm(Term courseTerm) {
        SubjectSectionEnrollment enrollment = mock(SubjectSectionEnrollment.class);
        ProgramEnrollment programEnrollment = mock(ProgramEnrollment.class);
        SubjectSection section = new SubjectSection();
        Subject subject = new Subject();
        subject.setId(3L);
        section.setSubject(subject);
        section.setTerm(courseTerm);
        Student student = new Student();
        student.setId(9L);
        when(enrollment.getStudent()).thenReturn(student);
        when(programEnrollment.getId()).thenReturn(7L);
        when(enrollment.getProgramEnrollment()).thenReturn(programEnrollment);
        when(enrollment.getSubjectSection()).thenReturn(section);
        when(sectionEnrollmentFinder.findProgramEnrollment(5L)).thenReturn(enrollment);
        return enrollment;
    }

    private Term term(long id) {
        Term term = new Term();
        term.setId(id);
        return term;
    }

    @Test
    void feeIsRequiredForTheTermOfTheExamBoardNotTheCourseTerm() {
        board(term(200L), NOW.plusDays(10));
        enrollmentInTerm(term(100L));
        org.mockito.Mockito.doThrow(new IllegalStateException("stop")).when(eligibilityEvaluator).requireEligibleToRegister(any(SubjectSectionEnrollment.class));

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L)).isInstanceOf(IllegalStateException.class);

        verify(feePort).requireForTerm(eq(7L), eq(200L), eq(EnrollmentFeeRequirementPurpose.EXAM_REGISTRATION));
        verify(feePort, never()).requireForTerm(anyLong(), eq(100L), any());
    }

    @Test
    void registrationAfterTheExamDateIsRejectedEvenWithAnApprovedLateExam() {
        board(term(200L), NOW.minusHours(1));
        enrollmentInTerm(term(100L));
        when(lateExamApprovalChecker.hasApprovedLateExam(anyLong(), anyLong())).thenReturn(true);

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L))
                .isInstanceOf(ExamBoardRegistrationAfterExamDateException.class);

        verify(lateExamApprovalChecker, never()).consumeApprovedLateExam(anyLong(), anyLong());
    }

    @Test
    void closedWindowWithoutApprovedLateExamIsRejected() {
        ExamBoard board = board(term(200L), NOW.plusDays(10));
        board.setRegistrationEnd(NOW.minusDays(1));
        enrollmentInTerm(term(100L));

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L))
                .isInstanceOf(ExamBoardRegistrationWindowClosedException.class);
    }

    @Test
    void openWindowDoesNotConsumeTheLateExamApproval() {
        board(term(200L), NOW.plusDays(10));
        enrollmentInTerm(term(100L));
        org.mockito.Mockito.doThrow(new IllegalStateException("stop")).when(eligibilityEvaluator).requireEligibleToRegister(any(SubjectSectionEnrollment.class));

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L)).isInstanceOf(IllegalStateException.class);

        verify(lateExamApprovalChecker, never()).consumeApprovedLateExam(anyLong(), anyLong());
    }

    @Test
    void rejectedRegistrationDoesNotConsumeTheLateExamApproval() {
        ExamBoard board = board(term(200L), NOW.plusDays(10));
        board.setRegistrationEnd(NOW.minusDays(1));
        enrollmentInTerm(term(100L));
        when(lateExamApprovalChecker.hasApprovedLateExam(7L, 3L)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("stop")).when(eligibilityEvaluator).requireEligibleToRegister(any(SubjectSectionEnrollment.class));

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L)).isInstanceOf(IllegalStateException.class);

        verify(lateExamApprovalChecker, never()).consumeApprovedLateExam(anyLong(), anyLong());
    }

    @Test
    void examBoardWithoutTermFallsBackToTheCurrentPeriodFee() {
        board(null, NOW.plusDays(10));
        enrollmentInTerm(term(100L));
        org.mockito.Mockito.doThrow(new IllegalStateException("stop")).when(eligibilityEvaluator).requireEligibleToRegister(any(SubjectSectionEnrollment.class));

        assertThatThrownBy(() -> manager.registerProgramEnrollment(10L, 5L)).isInstanceOf(IllegalStateException.class);

        verify(feePort).requireForCurrentPeriod(7L, EnrollmentFeeRequirementPurpose.EXAM_REGISTRATION);
        verify(feePort, never()).requireForTerm(anyLong(), anyLong(), any());
    }
}
