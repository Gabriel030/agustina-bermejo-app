package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.exception.SectionScheduleOverlapException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistCandidateEligibilityTest {

    private static final Long ENROLLMENT_ID = 10L;
    private static final Long PROGRAM_ENROLLMENT_ID = 20L;
    private static final Long SUBJECT_ID = 30L;

    @Mock
    private SubjectRetakeGuard subjectRetakeGuard;
    @Mock
    private ProgramPrerequisiteEvaluator prerequisiteEvaluator;
    @Mock
    private StudentScheduleOverlapGuard scheduleOverlapGuard;
    @Mock
    private CourseEnrollmentApplicationRepository courseApplicationRepository;
    @Mock
    private AdmissionInvoiceReceiptFinder admissionInvoiceReceiptFinder;

    private WaitlistCandidateEligibility eligibility;

    @BeforeEach
    void setUp() {
        eligibility = new WaitlistCandidateEligibility(subjectRetakeGuard, prerequisiteEvaluator, scheduleOverlapGuard,
                courseApplicationRepository, admissionInvoiceReceiptFinder);
        lenient().when(subjectRetakeGuard.isAlreadyPassed(anyLong(), anyLong(), anyLong())).thenReturn(false);
        lenient().when(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(anyLong(), anyLong())).thenReturn(true);
        lenient().when(scheduleOverlapGuard.findOverlapForProgramSection(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(Optional.empty());
    }

    @Test
    void candidateMeetingEveryRuleIsEligible() {
        WaitlistVerdict verdict = eligibility.evaluate(programCandidate());

        assertThat(verdict.eligible()).isTrue();
        assertThat(verdict.reason()).isNull();
    }

    @Test
    void alreadyPassedSubjectIsSkipped() {
        when(subjectRetakeGuard.isAlreadyPassed(PROGRAM_ENROLLMENT_ID, SUBJECT_ID, ENROLLMENT_ID)).thenReturn(true);

        assertSkippedBecause(eligibility.evaluate(programCandidate()), WaitlistVerdictReason.ALREADY_PASSED);
    }

    @Test
    void unmetPrerequisitesAreSkipped() {
        when(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(PROGRAM_ENROLLMENT_ID, SUBJECT_ID)).thenReturn(false);

        assertSkippedBecause(eligibility.evaluate(programCandidate()), WaitlistVerdictReason.PREREQUISITES_NOT_MET);
    }

    @Test
    void scheduleOverlapIsSkipped() {
        when(scheduleOverlapGuard.findOverlapForProgramSection(1L, 2L, 3L, ENROLLMENT_ID))
                .thenReturn(Optional.of(mock(SectionScheduleOverlapException.class)));

        assertSkippedBecause(eligibility.evaluate(programCandidate()), WaitlistVerdictReason.SCHEDULE_OVERLAP);
    }

    @Test
    void alreadyPassedWinsOverOtherViolations() {
        when(subjectRetakeGuard.isAlreadyPassed(PROGRAM_ENROLLMENT_ID, SUBJECT_ID, ENROLLMENT_ID)).thenReturn(true);
        lenient().when(prerequisiteEvaluator.areAllMetOrSubjectOutsidePlan(PROGRAM_ENROLLMENT_ID, SUBJECT_ID)).thenReturn(false);

        assertSkippedBecause(eligibility.evaluate(programCandidate()), WaitlistVerdictReason.ALREADY_PASSED);
    }

    @Test
    void courseWithReceiptPendingReviewIsSkipped() {
        when(courseApplicationRepository.findInvoiceIdsByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of(99L));
        when(admissionInvoiceReceiptFinder.isLatestReceiptPendingReview(99L)).thenReturn(true);

        assertSkippedBecause(eligibility.evaluate(courseCandidate()), WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING);
    }

    @Test
    void courseWithApprovedReceiptIsEligible() {
        when(courseApplicationRepository.findInvoiceIdsByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of(99L));
        when(admissionInvoiceReceiptFinder.isLatestReceiptPendingReview(99L)).thenReturn(false);

        assertThat(eligibility.evaluate(courseCandidate()).eligible()).isTrue();
    }

    @Test
    void courseWithoutAdmissionInvoiceIsEligible() {
        when(courseApplicationRepository.findInvoiceIdsByCourseEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of());

        assertThat(eligibility.evaluate(courseCandidate()).eligible()).isTrue();
    }

    @Test
    void courseSectionIsNotCheckedAgainstProgramRulesNorReceipts() {
        WaitlistVerdict verdict = eligibility.evaluate(new WaitlistCandidate(WaitlistKind.COURSE_SECTION, ENROLLMENT_ID, 1L,
                "Ana Paz", "1", LocalDateTime.now(), null, null, null, null));

        assertThat(verdict.eligible()).isTrue();
        verifyNoInteractions(subjectRetakeGuard, prerequisiteEvaluator, scheduleOverlapGuard, courseApplicationRepository);
    }

    private void assertSkippedBecause(WaitlistVerdict verdict, WaitlistVerdictReason reason) {
        assertThat(verdict.eligible()).isFalse();
        assertThat(verdict.reason()).isEqualTo(reason);
    }

    private WaitlistCandidate programCandidate() {
        return new WaitlistCandidate(WaitlistKind.PROGRAM_SECTION, ENROLLMENT_ID, 1L, "Ana Paz", "1", LocalDateTime.now(),
                PROGRAM_ENROLLMENT_ID, SUBJECT_ID, 2L, 3L);
    }

    private WaitlistCandidate courseCandidate() {
        return new WaitlistCandidate(WaitlistKind.COURSE, ENROLLMENT_ID, 1L, "Ana Paz", "1", LocalDateTime.now(),
                null, null, null, null);
    }
}
