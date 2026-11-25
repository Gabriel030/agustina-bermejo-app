package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProgramPrerequisiteBatchEvaluatorMinGradeTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long PROGRAM_ID = 9L;

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final DirectorApprovalRequestRepository approvalRepository = mock(DirectorApprovalRequestRepository.class);
    private final ProgramCurriculumFinder curriculumFinder = mock(ProgramCurriculumFinder.class);
    private final SubjectApprovalLookup lookup = mock(SubjectApprovalLookup.class);
    private final ProgramPrerequisiteBatchEvaluator evaluator = new ProgramPrerequisiteBatchEvaluator(
            enrollmentRepository, lookup, approvalRepository, curriculumFinder);

    @BeforeEach
    void setUp() {
        ProgramEnrollmentRepository.ProgramIdOfEnrollment row = mock(ProgramEnrollmentRepository.ProgramIdOfEnrollment.class);
        when(row.getEnrollmentId()).thenReturn(ENROLLMENT_ID);
        when(row.getProgramId()).thenReturn(PROGRAM_ID);
        when(enrollmentRepository.findProgramIdsByIdIn(any())).thenReturn(List.of(row));
        when(curriculumFinder.findProgramSubjects(PROGRAM_ID))
                .thenReturn(List.of(new ProgramSubjectView(33L, 3L, "Analisis", 4, 1, null, null)));
        when(approvalRepository.findProgramEnrollmentIdsWithRequest(any(), any(), any(), any())).thenReturn(List.of());
    }

    private PrerequisiteBatchResult assessWith(String minGrade, String approvedScore) {
        when(curriculumFinder.findProgramPrerequisites(PROGRAM_ID)).thenReturn(Map.of(33L, List.of(
                new PrerequisiteView(1L, "Algebra", RequirementType.APPROVED, "S1", Grade.of(new BigDecimal(minGrade))))));
        SubjectStanding standing = new SubjectStanding(List.of(new CourseworkEntry(
                EnrollmentStatus.APPROVED, Grade.of(new BigDecimal(approvedScore)), 11L)), null);
        when(lookup.findStandings(any(), any(), any()))
                .thenReturn(Map.of(ENROLLMENT_ID, Map.of(1L, standing)));
        return evaluator.assessAll(List.of(new PrerequisiteBatchQuery(100L, ENROLLMENT_ID, 3L))).get(100L);
    }

    @Test
    void detailCarriesMinimumGradeAndApprovedScore() {
        PrerequisiteDetail detail = assessWith("6", "5").details().get(0);

        assertThat(detail.minGrade()).isEqualTo(Grade.of(new BigDecimal("6")));
        assertThat(detail.approvedScore()).isEqualTo(Grade.of(new BigDecimal("5")));
        assertThat(detail.standing()).isEqualTo(PrerequisiteStanding.APPROVED);
    }

    @Test
    void approvalGateStaysIndependentOfMinimumGrade() {
        PrerequisiteBatchResult result = assessWith("6", "5");

        assertThat(result.outcome()).isEqualTo(PrerequisiteOutcome.MET);
        assertThat(result.details().get(0).metForApproval()).isTrue();
    }
}
