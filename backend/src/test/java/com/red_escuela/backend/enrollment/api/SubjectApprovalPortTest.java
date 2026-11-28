package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.exception.SubjectApprovedByEquivalenceException;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.PrerequisiteStatusRow;
import com.red_escuela.backend.enrollment.service.SubjectRetakeGuard;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubjectApprovalPortTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long OTHER_ENROLLMENT_ID = 6L;
    private static final Long PROGRAM_ID = 9L;
    private static final Long PLAN_ID = 17L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final SubjectEquivalenceRepository equivalenceRepository = mock(SubjectEquivalenceRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final SubjectApprovalLookup lookup = new SubjectApprovalLookup(sectionRepository, equivalenceRepository);
    private final List<PrerequisiteStatusRow> statusRows = new ArrayList<>();
    private final List<SubjectEquivalence> equivalences = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(sectionRepository.findPrerequisiteStatusRows(any(), any(), any())).thenReturn(statusRows);
        when(equivalenceRepository.findByProgramEnrollmentIdsAndStatus(any(), eq(SubjectEquivalenceStatus.ACTIVE)))
                .thenReturn(equivalences);
    }

    private void coursework(Long programEnrollmentId, Long subjectId, EnrollmentStatus status) {
        PrerequisiteStatusRow row = mock(PrerequisiteStatusRow.class);
        when(row.getProgramEnrollmentId()).thenReturn(programEnrollmentId);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getStatus()).thenReturn(status);
        statusRows.add(row);
    }

    private void recognized(Long programEnrollmentId, Long subjectId, String score) {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getId()).thenReturn(programEnrollmentId);
        equivalences.add(SubjectEquivalence.builder().id(subjectId * 10).programEnrollment(enrollment).subjectId(subjectId)
                .originInstitution("Universidad X").originSubjectName("Algebra")
                .originScore(score == null ? null : Grade.of(new BigDecimal(score)))
                .recognizedOn(LocalDate.of(2026, 3, 1)).resolutionNumber("RES-1").reason("Estudios previos")
                .status(SubjectEquivalenceStatus.ACTIVE).build());
    }

    @Test
    void standingsCombineCourseworkAndEquivalencesPerEnrollmentAndSubject() {
        coursework(ENROLLMENT_ID, 1L, EnrollmentStatus.REGULAR);
        coursework(OTHER_ENROLLMENT_ID, 1L, EnrollmentStatus.APPROVED);
        recognized(ENROLLMENT_ID, 2L, "7.00");

        Map<Long, Map<Long, SubjectStanding>> standings = lookup.findStandings(
                List.of(ENROLLMENT_ID, OTHER_ENROLLMENT_ID), List.of(1L, 2L), List.of(EnrollmentStatus.REGULAR, EnrollmentStatus.APPROVED));

        assertThat(standings.get(ENROLLMENT_ID).get(1L).isApproved()).isFalse();
        assertThat(standings.get(ENROLLMENT_ID).get(1L).isApprovedOrRegular()).isTrue();
        assertThat(standings.get(ENROLLMENT_ID).get(2L).isApprovedByEquivalence()).isTrue();
        assertThat(standings.get(ENROLLMENT_ID).get(2L).approvalSource()).isEqualTo(ApprovalSource.EQUIVALENCE);
        assertThat(standings.get(OTHER_ENROLLMENT_ID).get(1L).approvalSource()).isEqualTo(ApprovalSource.COURSEWORK);
        assertThat(standings.get(OTHER_ENROLLMENT_ID)).doesNotContainKey(2L);
    }

    @Test
    void standingsForSeveralEnrollmentsCostOneCourseworkQueryAndOneEquivalenceQuery() {
        recognized(ENROLLMENT_ID, 2L, null);

        lookup.findStandings(List.of(ENROLLMENT_ID, OTHER_ENROLLMENT_ID, 7L, 8L), List.of(1L, 2L, 3L),
                List.of(EnrollmentStatus.APPROVED));

        verify(sectionRepository, times(1)).findPrerequisiteStatusRows(any(), any(), any());
        verify(equivalenceRepository, times(1)).findByProgramEnrollmentIdsAndStatus(any(), eq(SubjectEquivalenceStatus.ACTIVE));
    }

    @Test
    void equivalencesOfSubjectsNotRequestedAreIgnored() {
        recognized(ENROLLMENT_ID, 99L, "9.00");

        Map<Long, Map<Long, SubjectStanding>> standings = lookup.findStandings(
                List.of(ENROLLMENT_ID), List.of(1L), List.of(EnrollmentStatus.APPROVED));

        assertThat(standings.get(ENROLLMENT_ID)).isEmpty();
    }

    @Test
    void equivalenceWithoutScoreStaysApprovedWithoutAScore() {
        recognized(ENROLLMENT_ID, 2L, null);

        SubjectStanding standing = lookup.findStandings(List.of(ENROLLMENT_ID), List.of(2L),
                List.of(EnrollmentStatus.APPROVED)).get(ENROLLMENT_ID).get(2L);

        assertThat(standing.isApproved()).isTrue();
        assertThat(standing.approvedScore()).isNull();
    }

    @Test
    void singleEvaluatorTreatsAnEquivalenceAsApprovedForBothRequirementTypes() {
        ProgramPrerequisiteEvaluator evaluator = new ProgramPrerequisiteEvaluator(enrollmentRepository, lookup,
                approvalRepository, curriculumFinder);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        CurriculumPlan plan = mock(CurriculumPlan.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getProgram()).thenReturn(program);
        when(enrollment.getPlan()).thenReturn(plan);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, 3L))
                .thenReturn(Optional.of(new ProgramSubjectView(33L, 3L, "Analisis", 4, 1, null, null)));
        when(curriculumFinder.findPrerequisites(33L)).thenReturn(List.of(
                new PrerequisiteView(1L, "Algebra", RequirementType.APPROVED, "S1", null),
                new PrerequisiteView(2L, "Fisica", RequirementType.PASSED_COURSEWORK, "S2", null)));
        recognized(ENROLLMENT_ID, 1L, "8.00");
        recognized(ENROLLMENT_ID, 2L, null);

        List<PrerequisiteAssessment> assessments = evaluator.assess(ENROLLMENT_ID, 3L);

        assertThat(assessments).extracting(PrerequisiteAssessment::standing)
                .containsExactly(PrerequisiteStanding.APPROVED, PrerequisiteStanding.APPROVED);
        assertThat(assessments).allMatch(PrerequisiteAssessment::met);
        assertThat(evaluator.areAllApprovedOrWaivedOrSubjectOutsidePlan(ENROLLMENT_ID, 3L)).isTrue();
    }

    @Test
    void batchEvaluatorTreatsAnEquivalenceAsApprovedPerEnrollment() {
        ProgramPrerequisiteBatchEvaluator evaluator = new ProgramPrerequisiteBatchEvaluator(enrollmentRepository, lookup,
                approvalRepository, curriculumFinder);
        ProgramEnrollmentRepository.PlanIdOfEnrollment first = mock(ProgramEnrollmentRepository.PlanIdOfEnrollment.class);
        when(first.getEnrollmentId()).thenReturn(ENROLLMENT_ID);
        when(first.getPlanId()).thenReturn(PLAN_ID);
        ProgramEnrollmentRepository.PlanIdOfEnrollment second = mock(ProgramEnrollmentRepository.PlanIdOfEnrollment.class);
        when(second.getEnrollmentId()).thenReturn(OTHER_ENROLLMENT_ID);
        when(second.getPlanId()).thenReturn(PLAN_ID);
        when(enrollmentRepository.findPlanIdsByIdIn(any())).thenReturn(List.of(first, second));
        when(curriculumFinder.findProgramSubjectsOfPlan(PLAN_ID))
                .thenReturn(List.of(new ProgramSubjectView(33L, 3L, "Analisis", 4, 1, null, null)));
        when(curriculumFinder.findPrerequisitesOfPlan(PLAN_ID)).thenReturn(Map.of(33L,
                List.of(new PrerequisiteView(1L, "Algebra", RequirementType.APPROVED, "S1", null))));
        when(approvalRepository.findProgramEnrollmentIdsWithRequest(any(), any(), any(), any())).thenReturn(List.of());
        recognized(ENROLLMENT_ID, 1L, null);

        Map<Long, PrerequisiteBatchResult> results = evaluator.assessAll(List.of(
                new PrerequisiteBatchQuery(100L, ENROLLMENT_ID, 3L),
                new PrerequisiteBatchQuery(200L, OTHER_ENROLLMENT_ID, 3L)));

        assertThat(results.get(100L).outcome()).isEqualTo(PrerequisiteOutcome.MET);
        assertThat(results.get(100L).details()).extracting(PrerequisiteDetail::standing)
                .containsExactly(PrerequisiteStanding.APPROVED);
        assertThat(results.get(200L).outcome()).isEqualTo(PrerequisiteOutcome.UNMET);
    }

    @Test
    void retakeGuardRejectsASubjectApprovedByEquivalence() {
        SubjectRetakeGuard guard = new SubjectRetakeGuard(sectionRepository, lookup);
        recognized(ENROLLMENT_ID, 4L, null);
        when(equivalenceRepository.findByProgramEnrollmentIdAndSubjectIdAndStatus(
                ENROLLMENT_ID, 4L, SubjectEquivalenceStatus.ACTIVE)).thenReturn(Optional.of(equivalences.get(0)));
        when(sectionRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireNotAlreadyPassed(ENROLLMENT_ID, 4L, null))
                .isInstanceOf(SubjectApprovedByEquivalenceException.class);
        assertThat(guard.isAlreadyPassed(ENROLLMENT_ID, 4L, null)).isTrue();
        assertThat(guard.isAlreadyPassed(ENROLLMENT_ID, 5L, null)).isFalse();
    }

    @Test
    void progressReaderCountsEquivalenceCreditsAndScoreAndListsItWithItsSource() {
        ProgramProgressReader reader = new ProgramProgressReader(enrollmentRepository, sectionRepository, curriculumFinder, lookup);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        CurriculumPlan plan = mock(CurriculumPlan.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(enrollment.getPlan()).thenReturn(plan);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(plan.getTotalCreditsRequired()).thenReturn(8);
        ProgramSubjectView first = new ProgramSubjectView(11L, 1L, "Algebra", 4, 1, null, null);
        ProgramSubjectView second = new ProgramSubjectView(22L, 2L, "Fisica", 4, 1, null, null);
        when(curriculumFinder.findActiveProgramSubjectsOfPlan(PLAN_ID)).thenReturn(List.of(first, second));
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(sectionRepository.findByProgramEnrollmentId(ENROLLMENT_ID)).thenReturn(List.of());
        coursework(ENROLLMENT_ID, 1L, EnrollmentStatus.APPROVED);
        recognized(ENROLLMENT_ID, 2L, "6.00");

        ProgramProgressView progress = reader.calculateProgress(ENROLLMENT_ID);
        List<ApprovedSubjectView> approved = reader.listApprovedSubjectsWithSource(ENROLLMENT_ID);

        assertThat(progress.accumulatedCredits()).isEqualTo(8);
        assertThat(progress.pendingMandatorySubjects()).isEmpty();
        assertThat(progress.eligibleToComplete()).isTrue();
        assertThat(progress.overallAverage().value()).isEqualByComparingTo("6.00");
        assertThat(approved).extracting(ApprovedSubjectView::subject).containsExactly(first, second);
        assertThat(approved).extracting(ApprovedSubjectView::approvalSource)
                .containsExactly(ApprovalSource.COURSEWORK, ApprovalSource.EQUIVALENCE);
        assertThat(reader.listApprovedSubjects(ENROLLMENT_ID)).containsExactly(first, second);
    }

    @Test
    void onlyHasFinalExamsLeftCountsAnEquivalenceAsCoursed() {
        ProgramProgressReader reader = new ProgramProgressReader(enrollmentRepository, sectionRepository, curriculumFinder, lookup);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        CurriculumPlan plan = mock(CurriculumPlan.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getProgram()).thenReturn(program);
        when(enrollment.getPlan()).thenReturn(plan);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(plan.getTotalCreditsRequired()).thenReturn(8);
        when(curriculumFinder.findActiveProgramSubjectsOfPlan(PLAN_ID)).thenReturn(List.of(
                new ProgramSubjectView(11L, 1L, "Algebra", 4, 1, null, null),
                new ProgramSubjectView(22L, 2L, "Fisica", 4, 1, null, null)));
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(sectionRepository.existsByProgramEnrollmentIdAndStatusIn(any(), any())).thenReturn(false);
        coursework(ENROLLMENT_ID, 1L, EnrollmentStatus.REGULAR);
        recognized(ENROLLMENT_ID, 2L, null);

        assertThat(reader.hasOnlyFinalExamsLeft(ENROLLMENT_ID)).isTrue();
    }

    @Test
    void emptyRequestedEnrollmentsOrSubjectsQueryNothing() {
        assertThat(lookup.findStandings(List.<Long>of(), List.of(1L), List.of(EnrollmentStatus.APPROVED))).isEmpty();
        assertThat(lookup.findStandings(List.of(ENROLLMENT_ID), List.<Long>of(), List.of(EnrollmentStatus.APPROVED))).isEmpty();
        verify(sectionRepository, times(0)).findPrerequisiteStatusRows(any(), any(), any());
    }

    @Test
    void averageDoesNotCountTheEquivalenceScoreOfASubjectAlsoApprovedByCoursework() {
        ProgramProgressReader reader = new ProgramProgressReader(enrollmentRepository, sectionRepository, curriculumFinder, lookup);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        CurriculumPlan plan = mock(CurriculumPlan.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(enrollment.getPlan()).thenReturn(plan);
        when(plan.getId()).thenReturn(PLAN_ID);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(plan.getTotalCreditsRequired()).thenReturn(4);
        when(curriculumFinder.findActiveProgramSubjectsOfPlan(PLAN_ID)).thenReturn(List.of(
                new ProgramSubjectView(11L, 1L, "Algebra", 4, 1, null, null)));
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        coursework(ENROLLMENT_ID, 1L, EnrollmentStatus.APPROVED);
        recognized(ENROLLMENT_ID, 1L, "2.00");

        assertThat(reader.calculateProgress(ENROLLMENT_ID).overallAverage()).isNull();
    }
}
