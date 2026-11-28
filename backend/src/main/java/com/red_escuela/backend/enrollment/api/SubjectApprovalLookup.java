package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.CurriculumProgressRow;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository.PrerequisiteStatusRow;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SubjectApprovalLookup {

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final SubjectEquivalenceRepository subjectEquivalenceRepository;

    public SubjectApprovalLookup(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
            SubjectEquivalenceRepository subjectEquivalenceRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.subjectEquivalenceRepository = subjectEquivalenceRepository;
    }

    public Map<Long, SubjectStanding> findHistory(Long programEnrollmentId) {
        Map<Long, List<CourseworkEntry>> courseworkBySubject = subjectSectionEnrollmentRepository
                .findCurriculumProgressRows(programEnrollmentId).stream()
                .collect(Collectors.groupingBy(CurriculumProgressRow::getSubjectId,
                        Collectors.mapping(row -> new CourseworkEntry(row.getStatus(), row.getFinalScore(), row.getEnrollmentId(), row.getFinalScoreDate()),
                                Collectors.toList())));
        Map<Long, RecognizedEquivalence> equivalenceBySubject = activeEquivalencesBySubject(
                List.of(programEnrollmentId), null).getOrDefault(programEnrollmentId, Map.of());
        Map<Long, SubjectStanding> history = new HashMap<>();
        Set<Long> subjectIds = new HashSet<>(courseworkBySubject.keySet());
        subjectIds.addAll(equivalenceBySubject.keySet());
        for (Long subjectId : subjectIds) {
            history.put(subjectId, new SubjectStanding(courseworkBySubject.getOrDefault(subjectId, List.of()),
                    equivalenceBySubject.get(subjectId)));
        }
        return history;
    }

    public Map<Long, Map<Long, SubjectStanding>> findStandings(Collection<Long> programEnrollmentIds,
            Collection<Long> subjectIds, Collection<EnrollmentStatus> courseworkStatuses) {
        return findStandings(programEnrollmentIds, subjectIds, courseworkStatuses, null);
    }

    public Map<Long, Map<Long, SubjectStanding>> findStandings(Collection<Long> programEnrollmentIds,
            Collection<Long> subjectIds, Collection<EnrollmentStatus> courseworkStatuses, Long ignoreRegularInTermId) {
        if (programEnrollmentIds.isEmpty() || subjectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, List<CourseworkEntry>>> coursework = new HashMap<>();
        for (PrerequisiteStatusRow row : subjectSectionEnrollmentRepository.findPrerequisiteStatusRows(
                programEnrollmentIds, subjectIds, courseworkStatuses)) {
            if (ignoreRegularInTermId != null && (row.getStatus() == EnrollmentStatus.REGULAR || row.getStatus() == EnrollmentStatus.APPROVAL_PENDING)
                    && ignoreRegularInTermId.equals(row.getTermId())) {
                continue;
            }
            coursework.computeIfAbsent(row.getProgramEnrollmentId(), id -> new HashMap<>())
                    .computeIfAbsent(row.getSubjectId(), id -> new ArrayList<>())
                    .add(new CourseworkEntry(row.getStatus(), row.getFinalScore(), row.getEnrollmentId(), row.getFinalScoreDate()));
        }
        Map<Long, Map<Long, RecognizedEquivalence>> equivalences = activeEquivalencesBySubject(programEnrollmentIds, subjectIds);
        Map<Long, Map<Long, SubjectStanding>> standings = new HashMap<>();
        for (Long programEnrollmentId : programEnrollmentIds) {
            Map<Long, List<CourseworkEntry>> courseworkBySubject = coursework.getOrDefault(programEnrollmentId, Map.of());
            Map<Long, RecognizedEquivalence> equivalenceBySubject = equivalences.getOrDefault(programEnrollmentId, Map.of());
            Set<Long> subjectsWithStanding = new HashSet<>(courseworkBySubject.keySet());
            subjectsWithStanding.addAll(equivalenceBySubject.keySet());
            Map<Long, SubjectStanding> bySubject = new HashMap<>();
            for (Long subjectId : subjectsWithStanding) {
                bySubject.put(subjectId, new SubjectStanding(courseworkBySubject.getOrDefault(subjectId, List.of()),
                        equivalenceBySubject.get(subjectId)));
            }
            standings.put(programEnrollmentId, bySubject);
        }
        return standings;
    }

    public Optional<RecognizedEquivalence> findActiveEquivalence(Long programEnrollmentId, Long subjectId) {
        return subjectEquivalenceRepository.findByProgramEnrollmentIdAndSubjectIdAndStatus(
                        programEnrollmentId, subjectId, SubjectEquivalenceStatus.ACTIVE)
                .map(SubjectApprovalLookup::recognizedOf);
    }

    private Map<Long, Map<Long, RecognizedEquivalence>> activeEquivalencesBySubject(
            Collection<Long> programEnrollmentIds, Collection<Long> subjectIds) {
        return subjectEquivalenceRepository
                .findByProgramEnrollmentIdsAndStatus(programEnrollmentIds, SubjectEquivalenceStatus.ACTIVE).stream()
                .filter(equivalence -> subjectIds == null || subjectIds.contains(equivalence.getSubjectId()))
                .collect(Collectors.groupingBy(equivalence -> equivalence.getProgramEnrollment().getId(),
                        Collectors.toMap(SubjectEquivalence::getSubjectId, SubjectApprovalLookup::recognizedOf,
                                (first, second) -> first)));
    }

    private static RecognizedEquivalence recognizedOf(SubjectEquivalence equivalence) {
        return new RecognizedEquivalence(equivalence.getId(), equivalence.getSubjectId(),
                equivalence.getOriginInstitution(), equivalence.getOriginSubjectName(), equivalence.getOriginScore(),
                equivalence.getRecognizedOn(), equivalence.getResolutionNumber());
    }
}
