package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.policy.PrerequisiteRequirementPolicy;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramPrerequisiteBatchEvaluator {

    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPrerequisiteBatchEvaluator(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
        this.programCurriculumFinder = programCurriculumFinder;
    }

    public Map<Long, PrerequisiteBatchResult> assessAll(Collection<PrerequisiteBatchQuery> queries) {
        if (queries.isEmpty()) {
            return Map.of();
        }
        Set<Long> programEnrollmentIds = queries.stream().map(PrerequisiteBatchQuery::programEnrollmentId)
                .collect(Collectors.toSet());
        Map<Long, Long> programIdByEnrollment = programEnrollmentRepository.findProgramIdsByIdIn(programEnrollmentIds).stream()
                .collect(Collectors.toMap(row -> row.getEnrollmentId(), row -> row.getProgramId()));
        Map<Long, Set<Long>> waivedBySubject = waivedProgramEnrollmentsBySubject(queries, programEnrollmentIds);
        Map<String, List<PrerequisiteView>> prerequisitesByPlanEntry = new HashMap<>();
        Map<String, Optional<ProgramSubjectView>> planEntries = new HashMap<>();
        Set<Long> prerequisiteSubjectIds = new HashSet<>();
        for (PrerequisiteBatchQuery query : queries) {
            Long programId = programIdByEnrollment.get(query.programEnrollmentId());
            String key = programId + ":" + query.subjectId();
            Optional<ProgramSubjectView> entry = planEntries.computeIfAbsent(
                    key, ignored -> programCurriculumFinder.findProgramSubject(programId, query.subjectId()));
            entry.ifPresent(programSubject -> prerequisitesByPlanEntry.computeIfAbsent(
                    key, ignored -> programCurriculumFinder.findPrerequisites(programSubject.id())));
        }
        prerequisitesByPlanEntry.values().forEach(list -> list.forEach(p -> prerequisiteSubjectIds.add(p.prerequisiteSubjectId())));
        Map<String, List<EnrollmentStatus>> statusesByStudentSubject = statusesOf(programEnrollmentIds, prerequisiteSubjectIds);
        Map<Long, PrerequisiteBatchResult> results = new HashMap<>();
        for (PrerequisiteBatchQuery query : queries) {
            String key = programIdByEnrollment.get(query.programEnrollmentId()) + ":" + query.subjectId();
            boolean waived = waivedBySubject.getOrDefault(query.subjectId(), Set.of()).contains(query.programEnrollmentId());
            List<PrerequisiteView> prerequisites = prerequisitesByPlanEntry.getOrDefault(key, List.of());
            results.put(query.sectionEnrollmentId(), resultOf(query, prerequisites, waived, statusesByStudentSubject));
        }
        return results;
    }

    private PrerequisiteBatchResult resultOf(PrerequisiteBatchQuery query, List<PrerequisiteView> prerequisites,
            boolean waived, Map<String, List<EnrollmentStatus>> statusesByStudentSubject) {
        List<PrerequisiteDetail> details = prerequisites.stream()
                .map(prerequisite -> detailOf(prerequisite, waived ? PrerequisiteStanding.WAIVED
                        : requirementPolicy.standingOf(statusesByStudentSubject.getOrDefault(
                                query.programEnrollmentId() + ":" + prerequisite.prerequisiteSubjectId(), List.of()))))
                .toList();
        if (waived && !details.isEmpty()) {
            return new PrerequisiteBatchResult(PrerequisiteOutcome.WAIVED, details);
        }
        boolean allMet = details.stream().allMatch(PrerequisiteDetail::metForApproval);
        return new PrerequisiteBatchResult(allMet ? PrerequisiteOutcome.MET : PrerequisiteOutcome.UNMET, details);
    }

    private PrerequisiteDetail detailOf(PrerequisiteView prerequisite, PrerequisiteStanding standing) {
        return new PrerequisiteDetail(prerequisite.prerequisiteSubjectId(), prerequisite.prerequisiteSubjectCode(),
                prerequisite.prerequisiteSubjectName(), prerequisite.requirementType(), standing,
                requirementPolicy.isMetForApproval(standing));
    }

    private Map<Long, Set<Long>> waivedProgramEnrollmentsBySubject(
            Collection<PrerequisiteBatchQuery> queries, Set<Long> programEnrollmentIds) {
        return queries.stream().map(PrerequisiteBatchQuery::subjectId).distinct().collect(Collectors.toMap(
                Function.identity(),
                subjectId -> new HashSet<>(directorApprovalRequestRepository.findProgramEnrollmentIdsWithRequest(
                        programEnrollmentIds, subjectId, ExceptionType.PREREQUISITE_WAIVER,
                        DirectorApprovalRequestStatus.APPROVED))));
    }

    private Map<String, List<EnrollmentStatus>> statusesOf(Set<Long> programEnrollmentIds, Set<Long> prerequisiteSubjectIds) {
        if (prerequisiteSubjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectSectionEnrollmentRepository.findPrerequisiteStatusRows(
                        programEnrollmentIds, prerequisiteSubjectIds, requirementPolicy.relevantStatuses()).stream()
                .collect(Collectors.groupingBy(row -> row.getProgramEnrollmentId() + ":" + row.getSubjectId(),
                        Collectors.mapping(row -> row.getStatus(), Collectors.toList())));
    }
}
