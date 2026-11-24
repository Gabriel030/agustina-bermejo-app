package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumEntryView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.api.ApprovalSource;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.CurriculumProgressRow;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurriculumProgressReaderEquivalenceTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final SubjectSectionEnrollmentRepository sectionRepository = mock(SubjectSectionEnrollmentRepository.class);
    private final SubjectEquivalenceRepository equivalenceRepository = mock(SubjectEquivalenceRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final CurriculumProgressReader reader = new CurriculumProgressReader(enrollmentRepository, approvalRepository,
            curriculumFinder, new SubjectApprovalLookup(sectionRepository, equivalenceRepository));
    private final Program program = mock(Program.class);
    private final ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
    private final List<CurriculumProgressRow> rows = new ArrayList<>();
    private final List<SubjectEquivalence> equivalences = new ArrayList<>();

    private List<ProgramCurriculumEntryView> plan = List.of();
    private Map<Long, List<PrerequisiteView>> prerequisites = Map.of();

    @BeforeEach
    void setUp() {
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(program.getName()).thenReturn("Tecnicatura");
        when(program.getTotalCreditsRequired()).thenReturn(12);
        when(curriculumFinder.findProgramCorequisites(PROGRAM_ID)).thenReturn(Map.of());
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of());
    }

    private CurriculumProgressView read() {
        when(curriculumFinder.findActiveCurriculumEntries(PROGRAM_ID)).thenReturn(plan);
        when(curriculumFinder.findProgramPrerequisites(PROGRAM_ID)).thenReturn(prerequisites);
        when(sectionRepository.findCurriculumProgressRows(ENROLLMENT_ID)).thenReturn(rows);
        when(equivalenceRepository.findByProgramEnrollmentIdsAndStatus(any(), eq(SubjectEquivalenceStatus.ACTIVE)))
                .thenReturn(equivalences);
        return reader.readProgress(ENROLLMENT_ID);
    }

    private static ProgramCurriculumEntryView entry(Long id, int credits) {
        return new ProgramCurriculumEntryView(id, id, "S" + id, "Materia " + id, credits, 1, null,
                ProgramSubjectCategory.MANDATORY, null);
    }

    private void approvedByCoursework(Long subjectId, String score) {
        CurriculumProgressRow row = mock(CurriculumProgressRow.class);
        when(row.getSubjectId()).thenReturn(subjectId);
        when(row.getStatus()).thenReturn(EnrollmentStatus.APPROVED);
        when(row.getFinalScore()).thenReturn(score == null ? null : Grade.of(new BigDecimal(score)));
        rows.add(row);
    }

    private void recognized(Long subjectId, String score) {
        equivalences.add(SubjectEquivalence.builder().id(subjectId * 100).programEnrollment(enrollment)
                .subjectId(subjectId).originInstitution("Universidad X").originSubjectName("Algebra")
                .originScore(score == null ? null : Grade.of(new BigDecimal(score)))
                .recognizedOn(LocalDate.of(2026, 3, 1)).resolutionNumber("RES-" + subjectId)
                .reason("Estudios previos").status(SubjectEquivalenceStatus.ACTIVE).build());
    }

    private CurriculumProgressView.SubjectView subjectOf(CurriculumProgressView view, Long id) {
        return view.subjects().stream().filter(subject -> subject.subjectId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void recognizedSubjectIsApprovedWithEquivalenceSourceAndPlanCredits() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        recognized(1L, "8.00");

        CurriculumProgressView view = read();

        CurriculumProgressView.SubjectView subject = subjectOf(view, 1L);
        assertThat(subject.state()).isEqualTo(CurriculumSubjectState.APPROVED);
        assertThat(subject.approvalSource()).isEqualTo(ApprovalSource.EQUIVALENCE);
        assertThat(subject.finalScore().value()).isEqualByComparingTo("8.00");
        assertThat(subject.equivalence().resolutionNumber()).isEqualTo("RES-1");
        assertThat(subject.equivalence().originInstitution()).isEqualTo("Universidad X");
        assertThat(subjectOf(view, 2L).approvalSource()).isNull();
        assertThat(subjectOf(view, 2L).equivalence()).isNull();
        assertThat(view.summary().accumulatedCredits()).isEqualTo(3);
        assertThat(view.summary().counts().approved()).isEqualTo(1);
        assertThat(view.summary().mandatoryApproved()).isEqualTo(1);
    }

    @Test
    void courseworkApprovalKeepsCourseworkSourceAndNoEquivalence() {
        plan = List.of(entry(1L, 3));
        approvedByCoursework(1L, "9.00");

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 1L).approvalSource()).isEqualTo(ApprovalSource.COURSEWORK);
        assertThat(subjectOf(view, 1L).equivalence()).isNull();
        assertThat(view.summary().approvedByEquivalence()).isZero();
        assertThat(view.summary().creditsByEquivalence()).isZero();
        assertThat(view.summary().accumulatedCreditsByCoursework()).isEqualTo(3);
        assertThat(view.summary().averageScore().value()).isEqualByComparingTo("9.00");
    }

    @Test
    void summarySplitsEquivalenceCreditsFromCourseworkCredits() {
        plan = List.of(entry(1L, 3), entry(2L, 4), entry(3L, 5));
        approvedByCoursework(1L, "8.00");
        recognized(2L, null);
        recognized(3L, "6.00");

        CurriculumProgressView.Summary summary = read().summary();

        assertThat(summary.accumulatedCredits()).isEqualTo(12);
        assertThat(summary.approvedByEquivalence()).isEqualTo(2);
        assertThat(summary.creditsByEquivalence()).isEqualTo(9);
        assertThat(summary.accumulatedCreditsByCoursework()).isEqualTo(3);
    }

    @Test
    void scoredEquivalenceEntersTheAverageAndUnscoredDoesNot() {
        plan = List.of(entry(1L, 3), entry(2L, 3), entry(3L, 3));
        approvedByCoursework(1L, "8.00");
        recognized(2L, "6.00");
        recognized(3L, null);

        CurriculumProgressView view = read();

        assertThat(view.summary().averageScore().value()).isEqualByComparingTo("7.00");
        assertThat(subjectOf(view, 3L).finalScore()).isNull();
        assertThat(subjectOf(view, 3L).state()).isEqualTo(CurriculumSubjectState.APPROVED);
        assertThat(view.summary().accumulatedCredits()).isEqualTo(9);
    }

    @Test
    void averageIsNullWhenNoApprovedSubjectHasAScore() {
        plan = List.of(entry(1L, 3));
        recognized(1L, null);

        assertThat(read().summary().averageScore()).isNull();
    }

    @Test
    void equivalenceSatisfiesBothApprovedAndPassedCourseworkRequirements() {
        plan = List.of(entry(1L, 3), entry(2L, 3), entry(3L, 3));
        prerequisites = Map.of(
                2L, List.of(new PrerequisiteView(1L, "Materia 1", RequirementType.APPROVED, "S1", null)),
                3L, List.of(new PrerequisiteView(1L, "Materia 1", RequirementType.PASSED_COURSEWORK, "S1", null)));
        recognized(1L, "7.00");

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.AVAILABLE);
        assertThat(subjectOf(view, 3L).state()).isEqualTo(CurriculumSubjectState.AVAILABLE);
    }

    @Test
    void dependentSubjectStaysBlockedWithoutTheEquivalence() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        prerequisites = Map.of(2L, List.of(new PrerequisiteView(1L, "Materia 1", RequirementType.APPROVED, "S1", null)));

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 2L).state()).isEqualTo(CurriculumSubjectState.BLOCKED);
        assertThat(subjectOf(view, 2L).blockedBy()).extracting(CurriculumProgressView.BlockingPrerequisite::standing)
                .containsExactly(PrerequisiteStanding.NOT_TAKEN);
    }

    @Test
    void equivalenceOutsideTheActivePlanAddsNoCreditsAndWarns() {
        plan = List.of(entry(1L, 3));
        recognized(99L, "8.00");

        CurriculumProgressView view = read();

        assertThat(view.summary().accumulatedCredits()).isZero();
        assertThat(view.summary().approvedByEquivalence()).isZero();
        assertThat(view.warnings()).extracting(CurriculumProgressView.Warning::code)
                .containsExactly("PLAN_CHANGED_AFTER_ENROLLMENT");
    }

    @Test
    void onlyActiveEquivalencesAreRequested() {
        plan = List.of(entry(1L, 3));

        read();

        verify(equivalenceRepository).findByProgramEnrollmentIdsAndStatus(any(), eq(SubjectEquivalenceStatus.ACTIVE));
    }

    @Test
    void waivedSubjectIsFlaggedWithoutBeingApproved() {
        plan = List.of(entry(1L, 3), entry(2L, 3));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of(2L));

        CurriculumProgressView view = read();

        assertThat(subjectOf(view, 2L).prerequisitesWaived()).isTrue();
        assertThat(subjectOf(view, 2L).state()).isNotEqualTo(CurriculumSubjectState.APPROVED);
        assertThat(subjectOf(view, 1L).prerequisitesWaived()).isFalse();
    }
}
