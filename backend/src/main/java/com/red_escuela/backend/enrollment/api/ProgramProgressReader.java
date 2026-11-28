package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.ElectiveGroupRequirementView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramProgressReader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final SubjectApprovalLookup subjectApprovalLookup;

    public ProgramProgressReader(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            ProgramCurriculumFinder programCurriculumFinder, SubjectApprovalLookup subjectApprovalLookup) {
        this.subjectApprovalLookup = subjectApprovalLookup;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public ProgramProgressView calculateProgress(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        Program program = enrollment.getProgram();
        CurriculumPlan plan = enrollment.getPlan();

        List<ProgramSubjectView> entries = programCurriculumFinder.findActiveProgramSubjectsOfPlan(plan.getId());
        Map<Long, SubjectStanding> standings = standingsOf(programEnrollmentId, entries, List.of(EnrollmentStatus.APPROVED));
        Set<Long> approvedSubjectIds = subjectIdsMatching(standings, SubjectStanding::isApproved);
        List<ProgramSubjectView> approved = entriesOfSubjects(entries, approvedSubjectIds);

        List<String> pendingMandatory = entries.stream()
                .filter(entry -> !entry.isElective() && !approvedSubjectIds.contains(entry.subjectId()))
                .map(ProgramSubjectView::subjectName)
                .toList();

        int accumulatedCredits = approved.stream().mapToInt(ProgramSubjectView::credits).sum();
        int requiredCredits = plan.getTotalCreditsRequired();

        List<ElectiveGroupProgressView> electiveGroups = programCurriculumFinder
                .findElectiveGroupRequirements(program.getId()).stream()
                .map(group -> toGroupProgress(group, approved))
                .toList();

        boolean eligibleToComplete = !entries.isEmpty()
                && pendingMandatory.isEmpty()
                && accumulatedCredits >= requiredCredits
                && electiveGroups.stream().allMatch(ElectiveGroupProgressView::fulfilled);

        List<Grade> approvedScores = approved.stream()
                .map(entry -> standings.get(entry.subjectId()).approvedScore())
                .filter(Objects::nonNull)
                .toList();

        return new ProgramProgressView(
                enrollment.getId(), accumulatedCredits, requiredCredits, pendingMandatory, electiveGroups,
                eligibleToComplete, hasActiveCoursework(programEnrollmentId), Grade.average(approvedScores).orElse(null));
    }

    public boolean hasOnlyFinalExamsLeft(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        Program program = enrollment.getProgram();
        CurriculumPlan plan = enrollment.getPlan();
        List<ProgramSubjectView> entries = programCurriculumFinder.findActiveProgramSubjectsOfPlan(plan.getId());
        if (entries.isEmpty()
                || subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                        programEnrollmentId, List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS))) {
            return false;
        }
        Set<Long> coursedSubjectIds = subjectIdsMatching(
                standingsOf(programEnrollmentId, entries, List.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR)),
                SubjectStanding::isApprovedOrRegular);
        List<ProgramSubjectView> coursed = entriesOfSubjects(entries, coursedSubjectIds);
        boolean mandatoryPending = entries.stream()
                .anyMatch(entry -> !entry.isElective() && !coursedSubjectIds.contains(entry.subjectId()));
        int coursedCredits = coursed.stream().mapToInt(ProgramSubjectView::credits).sum();
        boolean electiveGroupsMet = programCurriculumFinder.findElectiveGroupRequirements(program.getId()).stream()
                .allMatch(group -> countElectivesOfGroup(coursed, group) >= group.minRequired());
        return !coursed.isEmpty() && plan.getTotalCreditsRequired() > 0
                && !mandatoryPending && coursedCredits >= plan.getTotalCreditsRequired() && electiveGroupsMet;
    }

    public boolean hasActiveCoursework(Long programEnrollmentId) {
        return subjectSectionEnrollmentRepository.existsByProgramEnrollmentIdAndStatusIn(
                programEnrollmentId,
                List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR));
    }

    public List<ProgramSubjectView> listApprovedSubjects(Long programEnrollmentId) {
        return listApprovedSubjectsWithSource(programEnrollmentId).stream().map(ApprovedSubjectView::subject).toList();
    }

    public List<ApprovedSubjectView> listApprovedSubjectsWithSource(Long programEnrollmentId) {
        ProgramEnrollment enrollment = findOrThrow(programEnrollmentId);
        List<ProgramSubjectView> entries = programCurriculumFinder.findActiveProgramSubjectsOfPlan(enrollment.getPlan().getId());
        Map<Long, SubjectStanding> standings = standingsOf(programEnrollmentId, entries, List.of(EnrollmentStatus.APPROVED));
        return entries.stream()
                .filter(entry -> standings.getOrDefault(entry.subjectId(), SubjectStanding.NONE).isApproved())
                .map(entry -> new ApprovedSubjectView(entry,
                        standings.get(entry.subjectId()).approvalSource()))
                .toList();
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

    private Map<Long, SubjectStanding> standingsOf(Long programEnrollmentId, List<ProgramSubjectView> entries,
            List<EnrollmentStatus> statuses) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        List<Long> subjectIds = entries.stream().map(ProgramSubjectView::subjectId).distinct().toList();
        return subjectApprovalLookup.findStandings(List.of(programEnrollmentId), subjectIds, statuses)
                .getOrDefault(programEnrollmentId, Map.of());
    }

    private Set<Long> subjectIdsMatching(Map<Long, SubjectStanding> standings, Predicate<SubjectStanding> matches) {
        return standings.entrySet().stream().filter(entry -> matches.test(entry.getValue())).map(Map.Entry::getKey)
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
