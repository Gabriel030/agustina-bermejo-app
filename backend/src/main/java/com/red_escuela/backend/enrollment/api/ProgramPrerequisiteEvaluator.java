package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.policy.PrerequisiteRequirementPolicy;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramPrerequisiteEvaluator {

    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectApprovalLookup subjectApprovalLookup;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPrerequisiteEvaluator(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectApprovalLookup subjectApprovalLookup,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectApprovalLookup = subjectApprovalLookup;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public List<PrerequisiteAssessment> assess(Long programEnrollmentId, Long subjectId) {
        return assess(programEnrollmentId, subjectId, null);
    }

    public List<PrerequisiteAssessment> assess(Long programEnrollmentId, Long subjectId, Long targetTermId) {
        ProgramEnrollment programEnrollment = findProgramEnrollment(programEnrollmentId);
        Long programId = programEnrollment.getProgram().getId();
        ProgramSubjectView programSubject = programCurriculumFinder.findProgramSubject(programId, subjectId)
                .orElseThrow(() -> new SubjectNotInProgramPlanException(subjectId, programId));
        return assessPrerequisites(programEnrollmentId, subjectId, programSubject, targetTermId);
    }

    public boolean areAllMetOrSubjectOutsidePlan(Long programEnrollmentId, Long subjectId) {
        Long programId = findProgramEnrollment(programEnrollmentId).getProgram().getId();
        return programCurriculumFinder.findProgramSubject(programId, subjectId)
                .map(programSubject -> assessPrerequisites(programEnrollmentId, subjectId, programSubject, null).stream()
                        .allMatch(PrerequisiteAssessment::met))
                .orElse(true);
    }

    public boolean areAllApprovedOrWaivedOrSubjectOutsidePlan(Long programEnrollmentId, Long subjectId) {
        return findUnmetForApprovalOrEmptyOutsidePlan(programEnrollmentId, subjectId).isEmpty();
    }

    public List<PrerequisiteAssessment> findUnmetForApprovalOrEmptyOutsidePlan(Long programEnrollmentId, Long subjectId) {
        return findAssessmentsMatchingOrEmptyOutsidePlan(programEnrollmentId, subjectId,
                assessment -> !requirementPolicy.isMetForApproval(assessment.standing()));
    }

    public List<PrerequisiteAssessment> findUnmetForFinalExamOrEmptyOutsidePlan(Long programEnrollmentId, Long subjectId) {
        return findAssessmentsMatchingOrEmptyOutsidePlan(programEnrollmentId, subjectId,
                assessment -> !requirementPolicy.isMetForApproval(assessment.standing()) || !assessment.met());
    }

    private List<PrerequisiteAssessment> findAssessmentsMatchingOrEmptyOutsidePlan(
            Long programEnrollmentId, Long subjectId, Predicate<PrerequisiteAssessment> filter) {
        Long programId = findProgramEnrollment(programEnrollmentId).getProgram().getId();
        return programCurriculumFinder.findProgramSubject(programId, subjectId)
                .map(programSubject -> assessPrerequisites(programEnrollmentId, subjectId, programSubject, null).stream()
                        .filter(filter)
                        .toList())
                .orElse(List.of());
    }

    private ProgramEnrollment findProgramEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    private List<PrerequisiteAssessment> assessPrerequisites(
            Long programEnrollmentId, Long subjectId, ProgramSubjectView programSubject, Long targetTermId) {
        boolean waived = directorApprovalRequestRepository.hasApproved(
                programEnrollmentId, subjectId, ExceptionType.PREREQUISITE_WAIVER);
        List<PrerequisiteView> prerequisites = programCurriculumFinder.findPrerequisites(programSubject.id());
        Map<Long, SubjectStanding> standings = waived ? Map.of() : standingsOf(programEnrollmentId, prerequisites, targetTermId);
        return prerequisites.stream()
                .map(prerequisite -> {
                    if (waived) {
                        return assessment(prerequisite, PrerequisiteStanding.WAIVED, null);
                    }
                    SubjectStanding subjectStanding = standings.getOrDefault(
                            prerequisite.prerequisiteSubjectId(), SubjectStanding.NONE);
                    PrerequisiteStanding standing = requirementPolicy.standingOf(
                            subjectStanding.courseworkStatuses(), subjectStanding.isApprovedByEquivalence());
                    return assessment(prerequisite, standing, subjectStanding.scoreForPrerequisiteCheck());
                })
                .toList();
    }

    private Map<Long, SubjectStanding> standingsOf(Long programEnrollmentId, List<PrerequisiteView> prerequisites,
            Long targetTermId) {
        Set<Long> prerequisiteSubjectIds = prerequisites.stream().map(PrerequisiteView::prerequisiteSubjectId)
                .collect(Collectors.toSet());
        Map<Long, Map<Long, SubjectStanding>> standings = targetTermId == null
                ? subjectApprovalLookup.findStandings(List.of(programEnrollmentId), prerequisiteSubjectIds,
                        requirementPolicy.relevantStatuses())
                : subjectApprovalLookup.findStandings(List.of(programEnrollmentId), prerequisiteSubjectIds,
                        requirementPolicy.relevantStatuses(), targetTermId);
        return standings.getOrDefault(programEnrollmentId, Map.of());
    }

    private PrerequisiteAssessment assessment(PrerequisiteView prerequisite, PrerequisiteStanding standing,
            Grade approvedScore) {
        return new PrerequisiteAssessment(prerequisite.prerequisiteSubjectId(), prerequisite.prerequisiteSubjectName(),
                prerequisite.requirementType(), standing,
                requirementPolicy.isMet(prerequisite.requirementType(), standing, prerequisite.minGrade(), approvedScore),
                prerequisite.minGrade(), approvedScore);
    }
}
