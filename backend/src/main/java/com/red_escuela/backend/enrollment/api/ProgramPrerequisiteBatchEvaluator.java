package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.PrerequisiteView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.policy.PrerequisiteRequirementPolicy;
import com.red_escuela.backend.enrollment.policy.PrerequisiteStanding;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramPrerequisiteBatchEvaluator {

    private final PrerequisiteRequirementPolicy requirementPolicy = new PrerequisiteRequirementPolicy();
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectApprovalLookup subjectApprovalLookup;
    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;

    public ProgramPrerequisiteBatchEvaluator(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectApprovalLookup subjectApprovalLookup,
            DirectorApprovalRequestRepository directorApprovalRequestRepository,
            ProgramCurriculumFinder programCurriculumFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectApprovalLookup = subjectApprovalLookup;
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
        Set<Long> prerequisiteSubjectIds = new HashSet<>();
        for (Long programId : new HashSet<>(programIdByEnrollment.values())) {
            Map<Long, List<PrerequisiteView>> prerequisitesByProgramSubject = programCurriculumFinder.findProgramPrerequisites(programId);
            for (ProgramSubjectView entry : programCurriculumFinder.findProgramSubjects(programId)) {
                prerequisitesByPlanEntry.put(programId + ":" + entry.subjectId(),
                        prerequisitesByProgramSubject.getOrDefault(entry.id(), List.of()));
            }
        }
        for (PrerequisiteBatchQuery query : queries) {
            prerequisitesByPlanEntry.getOrDefault(programIdByEnrollment.get(query.programEnrollmentId()) + ":" + query.subjectId(), List.of())
                    .forEach(prerequisite -> prerequisiteSubjectIds.add(prerequisite.prerequisiteSubjectId()));
        }
        Map<Long, Map<Long, SubjectStanding>> standingsByEnrollment = standingsOf(programEnrollmentIds, prerequisiteSubjectIds);
        Map<Long, PrerequisiteBatchResult> results = new HashMap<>();
        for (PrerequisiteBatchQuery query : queries) {
            String key = programIdByEnrollment.get(query.programEnrollmentId()) + ":" + query.subjectId();
            boolean waived = waivedBySubject.getOrDefault(query.subjectId(), Set.of()).contains(query.programEnrollmentId());
            List<PrerequisiteView> prerequisites = prerequisitesByPlanEntry.getOrDefault(key, List.of());
            results.put(query.sectionEnrollmentId(), resultOf(query, prerequisites, waived, standingsByEnrollment));
        }
        return results;
    }

    private PrerequisiteBatchResult resultOf(PrerequisiteBatchQuery query, List<PrerequisiteView> prerequisites,
            boolean waived, Map<Long, Map<Long, SubjectStanding>> standingsByEnrollment) {
        List<PrerequisiteDetail> details = prerequisites.stream()
                .map(prerequisite -> detailOf(prerequisite, waived ? PrerequisiteStanding.WAIVED
                        : standingOf(standingsByEnrollment, query.programEnrollmentId(), prerequisite),
                        waived ? null : approvedScoreOf(standingsByEnrollment, query.programEnrollmentId(), prerequisite)))
                .toList();
        if (waived && !details.isEmpty()) {
            return new PrerequisiteBatchResult(PrerequisiteOutcome.WAIVED, details);
        }
        boolean allMet = details.stream().allMatch(PrerequisiteDetail::metForApproval);
        return new PrerequisiteBatchResult(allMet ? PrerequisiteOutcome.MET : PrerequisiteOutcome.UNMET, details);
    }

    private PrerequisiteDetail detailOf(PrerequisiteView prerequisite, PrerequisiteStanding standing,
            Grade approvedScore) {
        return new PrerequisiteDetail(prerequisite.prerequisiteSubjectId(), prerequisite.prerequisiteSubjectCode(),
                prerequisite.prerequisiteSubjectName(), prerequisite.requirementType(), standing,
                requirementPolicy.isMetForApproval(standing), prerequisite.minGrade(), approvedScore);
    }

    private Grade approvedScoreOf(Map<Long, Map<Long, SubjectStanding>> standingsByEnrollment,
            Long programEnrollmentId, PrerequisiteView prerequisite) {
        return standingsByEnrollment.getOrDefault(programEnrollmentId, Map.of())
                .getOrDefault(prerequisite.prerequisiteSubjectId(), SubjectStanding.NONE).scoreForPrerequisiteCheck();
    }

    private Map<Long, Set<Long>> waivedProgramEnrollmentsBySubject(
            Collection<PrerequisiteBatchQuery> queries, Set<Long> programEnrollmentIds) {
        return queries.stream().map(PrerequisiteBatchQuery::subjectId).distinct().collect(Collectors.toMap(
                Function.identity(),
                subjectId -> new HashSet<>(directorApprovalRequestRepository.findProgramEnrollmentIdsWithRequest(
                        programEnrollmentIds, subjectId, ExceptionType.PREREQUISITE_WAIVER,
                        DirectorApprovalRequestStatus.APPROVED))));
    }

    private PrerequisiteStanding standingOf(Map<Long, Map<Long, SubjectStanding>> standingsByEnrollment,
            Long programEnrollmentId, PrerequisiteView prerequisite) {
        SubjectStanding standing = standingsByEnrollment.getOrDefault(programEnrollmentId, Map.of())
                .getOrDefault(prerequisite.prerequisiteSubjectId(), SubjectStanding.NONE);
        return requirementPolicy.standingOf(standing.courseworkStatuses(), standing.isApprovedByEquivalence());
    }

    private Map<Long, Map<Long, SubjectStanding>> standingsOf(Set<Long> programEnrollmentIds, Set<Long> prerequisiteSubjectIds) {
        if (prerequisiteSubjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectApprovalLookup.findStandings(programEnrollmentIds, prerequisiteSubjectIds,
                requirementPolicy.relevantStatuses());
    }
}
