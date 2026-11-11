package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.enrollment.policy.PrerequisiteRequirementPolicy;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class ProgramPrerequisiteEvaluator {

    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPrerequisiteEvaluator(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public List<PrerequisiteAssessment> assess(Long programEnrollmentId, Long subjectId) {
        ProgramEnrollment programEnrollment = findProgramEnrollment(programEnrollmentId);
        Long programId = programEnrollment.getProgram().getId();
        ProgramSubjectView programSubject = programCurriculumFinder.findProgramSubject(programId, subjectId)
                .orElseThrow(() -> new SubjectNotInProgramPlanException(subjectId, programId));
        return assessPrerequisites(programEnrollmentId, subjectId, programSubject);
    }

    public boolean areAllMetOrSubjectOutsidePlan(Long programEnrollmentId, Long subjectId) {
        Long programId = findProgramEnrollment(programEnrollmentId).getProgram().getId();
        return programCurriculumFinder.findProgramSubject(programId, subjectId)
                .map(programSubject -> assessPrerequisites(programEnrollmentId, subjectId, programSubject).stream()
                        .allMatch(PrerequisiteAssessment::met))
                .orElse(true);
    }

    public boolean areAllApprovedOrWaivedOrSubjectOutsidePlan(Long programEnrollmentId, Long subjectId) {
        return findUnmetForApprovalOrEmptyOutsidePlan(programEnrollmentId, subjectId).isEmpty();
    }

    public List<PrerequisiteAssessment> findUnmetForApprovalOrEmptyOutsidePlan(Long programEnrollmentId, Long subjectId) {
        Long programId = findProgramEnrollment(programEnrollmentId).getProgram().getId();
        return programCurriculumFinder.findProgramSubject(programId, subjectId)
                .map(programSubject -> assessPrerequisites(programEnrollmentId, subjectId, programSubject).stream()
                        .filter(assessment -> !requirementPolicy.isMetForApproval(assessment.standing()))
                        .toList())
                .orElse(List.of());
    }

    private ProgramEnrollment findProgramEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    private List<PrerequisiteAssessment> assessPrerequisites(
            Long programEnrollmentId, Long subjectId, ProgramSubjectView programSubject) {
        boolean waived = directorApprovalRequestRepository.hasApproved(
                programEnrollmentId, subjectId, ExceptionType.PREREQUISITE_WAIVER);
        return programCurriculumFinder.findPrerequisites(programSubject.id()).stream()
                .map(prerequisite -> waived
                        ? assessment(prerequisite, PrerequisiteStanding.WAIVED)
                        : assessment(prerequisite, standingOf(programEnrollmentId, prerequisite)))
                .toList();
    }

    private PrerequisiteStanding standingOf(Long programEnrollmentId, PrerequisiteView prerequisite) {
        List<EnrollmentStatus> statuses = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, prerequisite.prerequisiteSubjectId(),
                        requirementPolicy.relevantStatuses())
                .stream()
                .map(SubjectSectionEnrollment::getStatus)
                .toList();
        return requirementPolicy.standingOf(statuses);
    }

    private PrerequisiteAssessment assessment(PrerequisiteView prerequisite, PrerequisiteStanding standing) {
        return new PrerequisiteAssessment(prerequisite.prerequisiteSubjectId(), prerequisite.prerequisiteSubjectName(),
                prerequisite.requirementType(), standing, requirementPolicy.isMet(prerequisite.requirementType(), standing));
    }
}
