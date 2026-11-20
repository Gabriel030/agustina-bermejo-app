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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramProgressReader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramProgressReader(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public ProgramProgressView calculateProgress(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        Program program = enrollment.getProgram();

        List<ProgramSubjectView> entries = programCurriculumFinder.findProgramSubjects(program.getId());
        Set<Long> approvedSubjectIds = subjectIdsWithStatus(programEnrollmentId, entries, List.of(EnrollmentStatus.APPROVED));
        List<ProgramSubjectView> approved = entriesOfSubjects(entries, approvedSubjectIds);

        List<String> pendingMandatory = entries.stream()
                .filter(entry -> !entry.isElective() && !approvedSubjectIds.contains(entry.subjectId()))
                .map(ProgramSubjectView::subjectName)
                .toList();

        int accumulatedCredits = approved.stream().mapToInt(ProgramSubjectView::credits).sum();
        int requiredCredits = program.getTotalCreditsRequired();

        List<ElectiveGroupProgressView> electiveGroups = programCurriculumFinder
                .findElectiveGroupRequirements(program.getId()).stream()
                .map(group -> toGroupProgress(group, approved))
                .toList();

        boolean eligibleToComplete = !entries.isEmpty()
                && pendingMandatory.isEmpty()
                && accumulatedCredits >= requiredCredits
                && electiveGroups.stream().allMatch(ElectiveGroupProgressView::fulfilled);

        List<Grade> finalGrades = subjectSectionEnrollmentRepository.findByProgramEnrollmentId(programEnrollmentId).stream()
                .map(SubjectSectionEnrollment::getFinalScore)
                .filter(Objects::nonNull)
                .toList();

        return new ProgramProgressView(
                enrollment.getId(), accumulatedCredits, requiredCredits, pendingMandatory, electiveGroups,
                eligibleToComplete, hasActiveCoursework(programEnrollmentId), Grade.average(finalGrades).orElse(null));
    }

    public boolean hasOnlyFinalExamsLeft(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        Program program = enrollment.getProgram();
        List<ProgramSubjectView> entries = programCurriculumFinder.findProgramSubjects(program.getId());
        if (entries.isEmpty()
                || subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                        programEnrollmentId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))) {
            return false;
        }
        Set<Long> coursedSubjectIds = subjectIdsWithStatus(programEnrollmentId, entries,
                List.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR));
        List<ProgramSubjectView> coursed = entriesOfSubjects(entries, coursedSubjectIds);
        boolean mandatoryPending = entries.stream()
                .anyMatch(entry -> !entry.isElective() && !coursedSubjectIds.contains(entry.subjectId()));
        int coursedCredits = coursed.stream().mapToInt(ProgramSubjectView::credits).sum();
        boolean electiveGroupsMet = programCurriculumFinder.findElectiveGroupRequirements(program.getId()).stream()
                .allMatch(group -> countElectivesOfGroup(coursed, group) >= group.minRequired());
        return !coursed.isEmpty() && program.getTotalCreditsRequired() > 0
                && !mandatoryPending && coursedCredits >= program.getTotalCreditsRequired() && electiveGroupsMet;
    }

    public boolean hasActiveCoursework(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                programEnrollmentId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
    }

    public List<ProgramSubjectView> listApprovedSubjects(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        List<ProgramSubjectView> entries = programCurriculumFinder.findProgramSubjects(enrollment.getProgram().getId());
        return entriesOfSubjects(entries, subjectIdsWithStatus(programEnrollmentId, entries, List.of(EnrollmentStatus.APPROVED)));
    }

    private ElectiveGroupProgressView toGroupProgress(ElectiveGroupRequirementView group, List<ProgramSubjectView> approved) {
        long approvedCount = countElectivesOfGroup(approved, group);
        return new ElectiveGroupProgressView(
                group.electiveGroupId(), group.name(),
                (int) approvedCount, group.minRequired(), group.maxAllowed(), approvedCount >= group.minRequired());
    }

    private long countElectivesOfGroup(List<ProgramSubjectView> subjects, ElectiveGroupRequirementView group) {
        return subjects.stream()
                .filter(entry -> entry.isElective() && entry.electiveGroupId().equals(group.electiveGroupId()))
                .count();
    }

    private Set<Long> subjectIdsWithStatus(Long programEnrollmentId, List<ProgramSubjectView> entries, List<EnrollmentStatus> statuses) {
        if (entries.isEmpty()) {
            return Set.of();
        }
        List<Long> subjectIds = entries.stream().map(ProgramSubjectView::subjectId).distinct().toList();
        return subjectSectionEnrollmentRepository
                .findPrerequisiteStatusRows(List.of(programEnrollmentId), subjectIds, statuses).stream()
                .map(SubjectSectionEnrollmentRepository.PrerequisiteStatusRow::getSubjectId)
                .collect(Collectors.toSet());
    }

    private List<ProgramSubjectView> entriesOfSubjects(List<ProgramSubjectView> entries, Set<Long> subjectIds) {
        return entries.stream().filter(entry -> subjectIds.contains(entry.subjectId())).toList();
    }

    private ProgramEnrollment findOrThrow(Long id) {
        return programEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
    }
}
