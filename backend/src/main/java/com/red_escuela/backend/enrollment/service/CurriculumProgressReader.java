package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.ElectiveGroupProgressView;
import com.red_escuela.backend.academicstructure.api.ProgramCorequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumEntryView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectState;
import com.red_escuela.backend.enrollment.policy.CurriculumSubjectStatePolicy;
import com.red_escuela.backend.enrollment.policy.PrerequisiteRequirementPolicy;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.CurriculumProgressRow;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class CurriculumProgressReader {

    private final CurriculumSubjectStatePolicy statePolicy = new CurriculumSubjectStatePolicy();
    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public CurriculumProgressReader(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public CurriculumProgressView readOwnProgress(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = findEnrollment(programEnrollmentId);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ProgramEnrollmentNotFoundException(programEnrollmentId);
        }
        return readProgressOf(enrollment);
    }

    public CurriculumProgressView readProgress(Long programEnrollmentId) {
        return readProgressOf(findEnrollment(programEnrollmentId));
    }

    private ProgramEnrollment findEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    private CurriculumProgressView readProgressOf(ProgramEnrollment enrollment) {
        Long programEnrollmentId = enrollment.getId();
        Program program = enrollment.getProgram();
        Long programId = program.getId();

        List<ProgramCurriculumEntryView> entries = programCurriculumFinder.findActiveCurriculumEntries(programId);
        Map<Long, List<PrerequisiteView>> prerequisites = programCurriculumFinder.findProgramPrerequisites(programId);
        Map<Long, List<ProgramCorequisiteView>> corequisites = programCurriculumFinder.findProgramCorequisites(programId);
        Map<Long, List<CurriculumProgressRow>> rowsBySubject = subjectSectionEnrollmentRepository
                .findCurriculumProgressRows(programEnrollmentId).stream()
                .collect(Collectors.groupingBy(CurriculumProgressRow::getSubjectId));
        Set<Long> waivedSubjectIds = new HashSet<>(directorApprovalRequestRepository.findSubjectIdsWithRequest(
                programEnrollmentId, ExceptionType.PREREQUISITE_WAIVER, DirectorApprovalRequestStatus.APPROVED));

        Map<Long, CurriculumSubjectState> stateByProgramSubject = new HashMap<>();
        Map<Long, List<CurriculumProgressView.BlockingPrerequisite>> unmetByProgramSubject = new HashMap<>();
        for (ProgramCurriculumEntryView entry : entries) {
            boolean waived = waivedSubjectIds.contains(entry.subjectId());
            List<CurriculumProgressView.BlockingPrerequisite> unmet = waived ? List.of()
                    : unmetPrerequisites(prerequisites.getOrDefault(entry.programSubjectId(), List.of()), rowsBySubject);
            unmetByProgramSubject.put(entry.programSubjectId(), unmet);
            stateByProgramSubject.put(entry.programSubjectId(),
                    statePolicy.stateOf(statusesOf(rowsBySubject.get(entry.subjectId())), unmet.isEmpty()));
        }

        List<CurriculumProgressView.SubjectView> subjects = new ArrayList<>();
        for (ProgramCurriculumEntryView entry : entries) {
            CurriculumSubjectState state = stateByProgramSubject.get(entry.programSubjectId());
            List<CurriculumProgressRow> rows = rowsBySubject.getOrDefault(entry.subjectId(), List.of());
            boolean showsBlockers = state == CurriculumSubjectState.BLOCKED || state == CurriculumSubjectState.FAILED;
            subjects.add(new CurriculumProgressView.SubjectView(
                    entry.programSubjectId(), entry.subjectId(), entry.subjectCode(), entry.subjectName(),
                    entry.credits(), entry.curricularYear(), entry.curricularTerm(), entry.category(),
                    entry.electiveGroupId(), state, approvedScoreOf(rows),
                    (int) rows.stream().filter(row -> statePolicy.countsAsAttempt(row.getStatus())).count(),
                    showsBlockers ? unmetByProgramSubject.get(entry.programSubjectId()) : List.of(),
                    corequisitesOf(corequisites.getOrDefault(entry.programSubjectId(), List.of()), stateByProgramSubject)));
        }

        return new CurriculumProgressView(enrollment.getId(), program.getName(),
                summaryOf(program.getTotalCreditsRequired(), entries, subjects, programId),
                subjects, warningsOf(program.getTotalCreditsRequired(), entries, rowsBySubject));
    }

    private CurriculumProgressView.Summary summaryOf(int requiredCredits, List<ProgramCurriculumEntryView> entries,
            List<CurriculumProgressView.SubjectView> subjects, Long programId) {
        List<CurriculumProgressView.SubjectView> approved = subjects.stream()
                .filter(subject -> subject.state() == CurriculumSubjectState.APPROVED).toList();
        int accumulatedCredits = approved.stream().mapToInt(CurriculumProgressView.SubjectView::credits).sum();
        Percentage completion = Percentage.ratio(Math.min(accumulatedCredits, requiredCredits), requiredCredits)
                .orElse(null);
        List<ElectiveGroupProgressView> groups = programCurriculumFinder.findElectiveGroupRequirements(programId).stream()
                .map(group -> {
                    long approvedCount = approved.stream()
                            .filter(subject -> group.electiveGroupId().equals(subject.electiveGroupId())).count();
                    return new ElectiveGroupProgressView(group.electiveGroupId(), group.name(), (int) approvedCount,
                            group.minRequired(), group.maxAllowed(), approvedCount >= group.minRequired());
                })
                .toList();
        long mandatoryTotal = entries.stream().filter(entry -> !entry.isElective()).count();
        long mandatoryApproved = approved.stream().filter(subject -> subject.electiveGroupId() == null).count();
        return new CurriculumProgressView.Summary(completion, accumulatedCredits, requiredCredits,
                Math.max(0, requiredCredits - accumulatedCredits), (int) mandatoryTotal, (int) mandatoryApproved,
                countsOf(subjects), groups);
    }

    private CurriculumProgressView.StateCounts countsOf(List<CurriculumProgressView.SubjectView> subjects) {
        Map<CurriculumSubjectState, Long> counts = subjects.stream()
                .collect(Collectors.groupingBy(CurriculumProgressView.SubjectView::state, Collectors.counting()));
        return new CurriculumProgressView.StateCounts(
                count(counts, CurriculumSubjectState.APPROVED), count(counts, CurriculumSubjectState.IN_PROGRESS),
                count(counts, CurriculumSubjectState.REGULAR_PENDING_FINAL), count(counts, CurriculumSubjectState.FAILED),
                count(counts, CurriculumSubjectState.AVAILABLE), count(counts, CurriculumSubjectState.BLOCKED));
    }

    private int count(Map<CurriculumSubjectState, Long> counts, CurriculumSubjectState state) {
        return counts.getOrDefault(state, 0L).intValue();
    }

    private List<CurriculumProgressView.BlockingPrerequisite> unmetPrerequisites(
            List<PrerequisiteView> prerequisites, Map<Long, List<CurriculumProgressRow>> rowsBySubject) {
        return prerequisites.stream()
                .map(prerequisite -> new CurriculumProgressView.BlockingPrerequisite(
                        prerequisite.prerequisiteSubjectId(), prerequisite.prerequisiteSubjectCode(),
                        prerequisite.prerequisiteSubjectName(), prerequisite.requirementType(),
                        requirementPolicy.standingOf(statusesOf(rowsBySubject.get(prerequisite.prerequisiteSubjectId())))))
                .filter(blocking -> !requirementPolicy.isMet(blocking.requirementType(), blocking.standing()))
                .toList();
    }

    private List<CurriculumProgressView.Corequisite> corequisitesOf(List<ProgramCorequisiteView> corequisites,
            Map<Long, CurriculumSubjectState> stateByProgramSubject) {
        return corequisites.stream()
                .filter(corequisite -> stateByProgramSubject.containsKey(corequisite.corequisiteProgramSubjectId()))
                .map(corequisite -> new CurriculumProgressView.Corequisite(
                        corequisite.corequisiteSubjectId(), corequisite.corequisiteSubjectCode(),
                        corequisite.corequisiteSubjectName(),
                        stateByProgramSubject.get(corequisite.corequisiteProgramSubjectId())))
                .toList();
    }

    private List<CurriculumProgressView.Warning> warningsOf(int requiredCredits, List<ProgramCurriculumEntryView> entries,
            Map<Long, List<CurriculumProgressRow>> rowsBySubject) {
        List<CurriculumProgressView.Warning> warnings = new ArrayList<>();
        if (requiredCredits == 0) {
            warnings.add(new CurriculumProgressView.Warning("PLAN_WITHOUT_CREDITS",
                    "El plan de estudios no define créditos requeridos."));
        }
        Set<Long> planSubjectIds = entries.stream().map(ProgramCurriculumEntryView::subjectId)
                .collect(Collectors.toSet());
        boolean approvedOutsidePlan = rowsBySubject.entrySet().stream()
                .filter(subjectRows -> !planSubjectIds.contains(subjectRows.getKey()))
                .anyMatch(subjectRows -> statusesOf(subjectRows.getValue()).contains(EnrollmentStatus.APPROVED));
        if (approvedOutsidePlan) {
            warnings.add(new CurriculumProgressView.Warning("PLAN_CHANGED_AFTER_ENROLLMENT",
                    "El plan de estudios cambió desde la inscripción: hay materias aprobadas que ya no forman parte del plan y no suman créditos."));
        }
        return warnings;
    }

    private Grade approvedScoreOf(List<CurriculumProgressRow> rows) {
        return rows.stream().filter(row -> row.getStatus() == EnrollmentStatus.APPROVED)
                .map(CurriculumProgressRow::getFinalScore).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private List<EnrollmentStatus> statusesOf(List<CurriculumProgressRow> rows) {
        return rows == null ? List.of() : rows.stream().map(CurriculumProgressRow::getStatus).toList();
    }
}
