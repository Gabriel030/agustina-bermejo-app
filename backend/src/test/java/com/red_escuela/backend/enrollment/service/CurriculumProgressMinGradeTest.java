package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumEntryView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.enrollment.api.CourseworkEntry;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.PrerequisiteAssessment;
import com.red_escuela.backend.enrollment.api.ProgramPrerequisiteEvaluator;
import com.red_escuela.backend.enrollment.api.RecognizedEquivalence;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.enrollment.api.SubjectStanding;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurriculumProgressMinGradeTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;
    private static final Long PREREQUISITE_SUBJECT_ID = 1L;
    private static final Long TARGET_SUBJECT_ID = 2L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final SubjectApprovalLookup lookup = mock(SubjectApprovalLookup.class);
    private final CurriculumProgressReader reader = new CurriculumProgressReader(
            enrollmentRepository, approvalRepository, curriculumFinder, lookup);
    private final ProgramPrerequisiteEvaluator evaluator = new ProgramPrerequisiteEvaluator(
            enrollmentRepository, lookup, approvalRepository, curriculumFinder);

    record Scenario(String name, RequirementType type, Grade minGrade, List<CourseworkEntry> coursework,
                    RecognizedEquivalence equivalence, boolean waived, boolean expectedBlocked) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    private static CourseworkEntry approved(String score) {
        return new CourseworkEntry(EnrollmentStatus.APPROVED, score == null ? null : grade(score), 11L);
    }

    private static RecognizedEquivalence equivalence(String originScore) {
        return new RecognizedEquivalence(7L, PREREQUISITE_SUBJECT_ID, "Otra institucion", "Materia previa",
                originScore == null ? null : grade(originScore), LocalDate.of(2026, 3, 1), "RES-1");
    }

    static Stream<Scenario> scenarios() {
        Grade six = grade("6");
        return Stream.of(
                new Scenario("score below minimum blocks", RequirementType.APPROVED, six,
                        List.of(approved("5")), null, false, true),
                new Scenario("score just below minimum blocks", RequirementType.APPROVED, six,
                        List.of(approved("5.99")), null, false, true),
                new Scenario("score equal to minimum is available", RequirementType.APPROVED, six,
                        List.of(approved("6")), null, false, false),
                new Scenario("score above minimum is available", RequirementType.APPROVED, six,
                        List.of(approved("9")), null, false, false),
                new Scenario("approved without score is available", RequirementType.APPROVED, six,
                        List.of(approved(null)), null, false, false),
                new Scenario("no minimum is available with low score", RequirementType.APPROVED, null,
                        List.of(approved("2")), null, false, false),
                new Scenario("best of several approved attempts decides", RequirementType.APPROVED, six,
                        List.of(approved("4"), approved("7")), null, false, false),
                new Scenario("waiver covers a score below minimum", RequirementType.APPROVED, six,
                        List.of(approved("3")), null, true, false),
                new Scenario("equivalence origin score below minimum blocks", RequirementType.APPROVED, six,
                        List.of(), equivalence("5"), false, true),
                new Scenario("equivalence origin score equal to minimum is available", RequirementType.APPROVED, six,
                        List.of(), equivalence("6"), false, false),
                new Scenario("equivalence without origin score is available", RequirementType.APPROVED, six,
                        List.of(), equivalence(null), false, false),
                new Scenario("coursework requirement ignores minimum", RequirementType.PASSED_COURSEWORK, six,
                        List.of(new CourseworkEntry(EnrollmentStatus.REGULAR, null, 12L)), null, false, false),
                new Scenario("approved requirement with only regular still blocks", RequirementType.APPROVED, six,
                        List.of(new CourseworkEntry(EnrollmentStatus.REGULAR, null, 12L)), null, false, true));
    }

    @BeforeEach
    void setUp() {
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        Program program = mock(Program.class);
        when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(enrollment.getId()).thenReturn(ENROLLMENT_ID);
        when(enrollment.getProgram()).thenReturn(program);
        when(program.getId()).thenReturn(PROGRAM_ID);
        when(program.getName()).thenReturn("Tecnicatura");
        when(program.getTotalCreditsRequired()).thenReturn(8);
        when(curriculumFinder.findProgramCorequisites(PROGRAM_ID)).thenReturn(Map.of());
        when(curriculumFinder.findElectiveGroupRequirements(PROGRAM_ID)).thenReturn(List.of());
        when(curriculumFinder.findActiveCurriculumEntries(PROGRAM_ID)).thenReturn(List.of(entry(1L), entry(2L)));
        when(curriculumFinder.findProgramSubject(PROGRAM_ID, TARGET_SUBJECT_ID)).thenReturn(Optional.of(
                new ProgramSubjectView(TARGET_SUBJECT_ID, TARGET_SUBJECT_ID, "Materia 2", 4, 1, null, null)));
        when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any())).thenReturn(List.of());
    }

    private static ProgramCurriculumEntryView entry(Long id) {
        return new ProgramCurriculumEntryView(id, id, "S" + id, "Materia " + id, 4, 1, null,
                ProgramSubjectCategory.MANDATORY, null);
    }

    private void arrange(Scenario scenario) {
        PrerequisiteView prerequisite = new PrerequisiteView(PREREQUISITE_SUBJECT_ID, "Materia 1",
                scenario.type(), "S1", scenario.minGrade());
        SubjectStanding standing = new SubjectStanding(scenario.coursework(), scenario.equivalence());
        when(curriculumFinder.findProgramPrerequisites(PROGRAM_ID))
                .thenReturn(Map.of(TARGET_SUBJECT_ID, List.of(prerequisite)));
        when(curriculumFinder.findPrerequisites(TARGET_SUBJECT_ID)).thenReturn(List.of(prerequisite));
        when(lookup.findHistory(ENROLLMENT_ID)).thenReturn(Map.of(PREREQUISITE_SUBJECT_ID, standing));
        when(lookup.findStandings(any(), any(), any()))
                .thenReturn(Map.of(ENROLLMENT_ID, Map.of(PREREQUISITE_SUBJECT_ID, standing)));
        if (scenario.waived()) {
            when(approvalRepository.findSubjectIdsWithRequest(any(), any(), any()))
                    .thenReturn(List.of(TARGET_SUBJECT_ID));
            when(approvalRepository.hasApproved(ENROLLMENT_ID, TARGET_SUBJECT_ID, ExceptionType.PREREQUISITE_WAIVER))
                    .thenReturn(true);
        }
    }

    private CurriculumProgressView.SubjectView target() {
        return reader.readProgress(ENROLLMENT_ID).subjects().stream()
                .filter(subject -> subject.subjectId().equals(TARGET_SUBJECT_ID)).findFirst().orElseThrow();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void progressStateFollowsMinimumGrade(Scenario scenario) {
        arrange(scenario);

        CurriculumProgressView.SubjectView subject = target();

        assertThat(subject.state()).isEqualTo(
                scenario.expectedBlocked() ? CurriculumSubjectState.BLOCKED : CurriculumSubjectState.AVAILABLE);
        assertThat(subject.blockedBy()).hasSize(scenario.expectedBlocked() ? 1 : 0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void progressAgreesWithEnrollmentEvaluator(Scenario scenario) {
        arrange(scenario);

        boolean enrollmentRejects = !evaluator.areAllMetOrSubjectOutsidePlan(ENROLLMENT_ID, TARGET_SUBJECT_ID);

        assertThat(target().state() == CurriculumSubjectState.BLOCKED).isEqualTo(enrollmentRejects);
    }

    @Test
    void blockingPrerequisiteExposesMinimumAndApprovedScore() {
        Scenario scenario = scenarios().findFirst().orElseThrow();
        arrange(scenario);

        CurriculumProgressView.BlockingPrerequisite blocker = target().blockedBy().get(0);

        assertThat(blocker.standing()).isEqualTo(PrerequisiteStanding.APPROVED);
        assertThat(blocker.minGrade()).isEqualTo(grade("6"));
        assertThat(blocker.approvedScore()).isEqualTo(grade("5"));
    }

    @Test
    void enrollmentAssessmentReportsTheSameMinimumAndScoreAsProgress() {
        arrange(scenarios().findFirst().orElseThrow());

        PrerequisiteAssessment assessment = evaluator.assess(ENROLLMENT_ID, TARGET_SUBJECT_ID).get(0);
        CurriculumProgressView.BlockingPrerequisite blocker = target().blockedBy().get(0);

        assertThat(assessment.met()).isFalse();
        assertThat(blocker.minGrade()).isEqualTo(assessment.minGrade());
        assertThat(blocker.approvedScore()).isEqualTo(assessment.approvedScore());
    }
}
