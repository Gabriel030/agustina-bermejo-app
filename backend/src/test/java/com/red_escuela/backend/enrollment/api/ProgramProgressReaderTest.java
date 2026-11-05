package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.ElectiveGroupRequirementView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProgramProgressReaderTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final ProgramProgressReader reader =
            new ProgramProgressReader(enrollmentRepository, sectionRepository, curriculumFinder);
    private final Program program = mock(Program.class);
    private final ProgramEnrollment enrollment = mock(ProgramEnrollment.class);

    private final ProgramSubjectView mandatoryOne = subject(1L, "Algebra", 4, null);
    private final ProgramSubjectView mandatoryTwo = subject(2L, "Historia", 6, null);
    private final ProgramSubjectView electiveOne = subject(3L, "Taller", 2, 70L);
    private final ProgramSubjectView electiveTwo = subject(4L, "Seminario", 2, 70L);

    @BeforeEach
    void setUp() {
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(program.getTotalCreditsRequired()).thenReturn(12);
        when(curriculumFinder.findProgramSubjects(PROGRAM_ID))
                .thenReturn(List.of(mandatoryOne, mandatoryTwo, electiveOne, electiveTwo));
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID))
                .thenReturn(List.of(new ElectiveGroupRequirementView(70L, "Optativas", 1, 2)));
        when(sectionRepository.findByProgramEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of());
        when(sectionRepository.existsByProgramEnrollmentIdAndStatusIn(eq(ENROLLMENT_ID), any())).thenReturn(false);
        when(sectionRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                eq(ENROLLMENT_ID), any(), any())).thenReturn(List.of());
    }

    private static ProgramSubjectView subject(Long id, String name, int credits, Long groupId) {
        return new ProgramSubjectView(id, id, name, credits, 1, null, groupId);
    }

    private void markAs(ProgramSubjectView subject, EnrollmentStatus... statuses) {
        when(sectionRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                ENROLLMENT_ID, subject.subjectId(), List.of(statuses)))
                .thenReturn(List.of(mock(SubjectSectionEnrollment.class)));
    }

    private void markAsCoursed(ProgramSubjectView... subjects) {
        for (ProgramSubjectView subject : subjects) {
            markAs(subject, EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR);
        }
    }

    @Test
    void progressWithAllRequirementsMetIsEligibleToComplete() {
        markAs(mandatoryOne, EnrollmentStatus.APPROVED);
        markAs(mandatoryTwo, EnrollmentStatus.APPROVED);
        markAs(electiveOne, EnrollmentStatus.APPROVED);

        ProgramProgressView progress = reader.calculateProgress(ENROLLMENT_ID);

        assertThat(progress.eligibleToComplete()).isTrue();
        assertThat(progress.accumulatedCredits()).isEqualTo(12);
        assertThat(progress.requiredCredits()).isEqualTo(12);
        assertThat(progress.programEnrollmentId()).isEqualTo(ENROLLMENT_ID);
    }

    @Test
    void accumulatedCreditsBelowRequiredBlockCompletion() {
        markAs(mandatoryOne, EnrollmentStatus.APPROVED);
        markAs(mandatoryTwo, EnrollmentStatus.APPROVED);
        markAs(electiveOne, EnrollmentStatus.APPROVED);
        when(program.getTotalCreditsRequired()).thenReturn(13);

        assertThat(reader.calculateProgress(ENROLLMENT_ID).eligibleToComplete()).isFalse();
    }

    @Test
    void pendingMandatorySubjectsAreListedByNameAndBlockCompletion() {
        markAs(mandatoryOne, EnrollmentStatus.APPROVED);
        markAs(electiveOne, EnrollmentStatus.APPROVED);
        when(program.getTotalCreditsRequired()).thenReturn(6);

        ProgramProgressView progress = reader.calculateProgress(ENROLLMENT_ID);

        assertThat(progress.pendingMandatorySubjects()).containsExactly("Historia");
        assertThat(progress.eligibleToComplete()).isFalse();
    }

    @Test
    void unmetElectiveGroupBlocksCompletionAndReportsCounts() {
        markAs(mandatoryOne, EnrollmentStatus.APPROVED);
        markAs(mandatoryTwo, EnrollmentStatus.APPROVED);
        when(program.getTotalCreditsRequired()).thenReturn(10);

        ProgramProgressView progress = reader.calculateProgress(ENROLLMENT_ID);

        assertThat(progress.eligibleToComplete()).isFalse();
        assertThat(progress.electiveGroups()).containsExactly(
                new ElectiveGroupProgressView(70L, "Optativas", 0, 1, 2, false));
    }

    @Test
    void emptyStudyPlanIsNeverEligibleToComplete() {
        when(curriculumFinder.findProgramSubjects(PROGRAM_ID)).thenReturn(List.of());
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(program.getTotalCreditsRequired()).thenReturn(0);

        assertThat(reader.calculateProgress(ENROLLMENT_ID).eligibleToComplete()).isFalse();
    }

    @Test
    void overallAverageIgnoresMissingFinalScores() {
        SubjectSectionEnrollment graded = mock(SubjectSectionEnrollment.class);
        when(graded.getFinalScore()).thenReturn(Grade.of(new BigDecimal("8.00")));
        SubjectSectionEnrollment otherGraded = mock(SubjectSectionEnrollment.class);
        when(otherGraded.getFinalScore()).thenReturn(Grade.of(new BigDecimal("6.00")));
        SubjectSectionEnrollment ungraded = mock(SubjectSectionEnrollment.class);
        when(sectionRepository.findByProgramEnrollmentId(ENROLLMENT_ID))
                .thenReturn(List.of(graded, otherGraded, ungraded));

        assertThat(reader.calculateProgress(ENROLLMENT_ID).overallAverage().value()).isEqualByComparingTo("7.00");
    }

    @Test
    void overallAverageIsNullWithoutFinalScores() {
        assertThat(reader.calculateProgress(ENROLLMENT_ID).overallAverage()).isNull();
    }

    @Test
    void progressReportsRegularWhenSomeSubjectIsStillActive() {
        when(sectionRepository.existsByProgramEnrollmentIdAndStatusIn(ENROLLMENT_ID,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR)))
                .thenReturn(true);

        assertThat(reader.calculateProgress(ENROLLMENT_ID).regular()).isTrue();
        assertThat(reader.hasActiveEnrollment(ENROLLMENT_ID)).isTrue();
    }

    @Test
    void listApprovedSubjectsReturnsOnlyApprovedEntries() {
        markAs(mandatoryOne, EnrollmentStatus.APPROVED);
        markAs(mandatoryTwo, EnrollmentStatus.REGULAR);

        assertThat(reader.listApprovedSubjects(ENROLLMENT_ID)).containsExactly(mandatoryOne);
    }

    @Test
    void onlyFinalExamsLeftWhenEverythingIsCoursedAndNothingIsRunning() {
        markAsCoursed(mandatoryOne, mandatoryTwo, electiveOne);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isTrue();
    }

    @Test
    void notOnlyFinalExamsLeftWhileASubjectIsEnrolledOrInProgress() {
        markAsCoursed(mandatoryOne, mandatoryTwo, electiveOne);
        when(sectionRepository.existsByProgramEnrollmentIdAndStatusIn(ENROLLMENT_ID,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))).thenReturn(true);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void notOnlyFinalExamsLeftWhenAMandatorySubjectWasNeverCoursed() {
        markAsCoursed(mandatoryOne, electiveOne);
        when(program.getTotalCreditsRequired()).thenReturn(6);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void notOnlyFinalExamsLeftWhenCoursedCreditsAreBelowRequired() {
        markAsCoursed(mandatoryOne, mandatoryTwo, electiveOne);
        when(program.getTotalCreditsRequired()).thenReturn(13);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void notOnlyFinalExamsLeftWhenAnElectiveGroupIsUnmet() {
        markAsCoursed(mandatoryOne, mandatoryTwo);
        when(program.getTotalCreditsRequired()).thenReturn(10);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void notOnlyFinalExamsLeftWhenProgramRequiresNoCredits() {
        markAsCoursed(mandatoryOne, mandatoryTwo, electiveOne);
        when(program.getTotalCreditsRequired()).thenReturn(0);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void notOnlyFinalExamsLeftWhenStudyPlanIsEmpty() {
        when(curriculumFinder.findProgramSubjects(PROGRAM_ID)).thenReturn(List.of());

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isFalse();
    }

    @Test
    void unknownEnrollmentThrowsNotFound() {
        when(enrollmentRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.calculateProgress(77L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
        assertThatThrownBy(() -> reader.listApprovedSubjects(77L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
        assertThatThrownBy(() -> reader.hasOnlyFinalExamsLeft(77L)).isInstanceOf(ProgramEnrollmentNotFoundException.class);
    }
}
