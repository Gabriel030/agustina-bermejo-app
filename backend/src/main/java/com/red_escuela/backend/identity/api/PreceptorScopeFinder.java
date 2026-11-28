package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class PreceptorScopeFinder {

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;

    public PreceptorScopeFinder(PreceptorAssignmentRepository preceptorAssignmentRepository) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
    }

    public Set<Long> assignedSubjectSectionIds(Long personId) {
        return idsOf(personId, PreceptorAssignment::getSubjectSectionId);
    }

    public Set<Long> assignedCourseSectionIds(Long personId) {
        return idsOf(personId, PreceptorAssignment::getCourseSectionId);
    }

    public Set<Long> assignedProgramCohortIds(Long personId) {
        return idsOf(personId, PreceptorAssignment::getProgramCohortId);
    }

    public Set<Long> responsiblePersonIdsOfCohort(Long programCohortId) {
        return preceptorAssignmentRepository.findByProgramCohortId(programCohortId).stream()
                .map(PreceptorAssignment::getPersonId)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Long> subjectSectionIdsWithPreceptor(Collection<Long> subjectSectionIds) {
        if (subjectSectionIds.isEmpty()) {
            return Set.of();
        }
        return preceptorAssignmentRepository.findBySubjectSectionIdIn(subjectSectionIds).stream()
                .map(PreceptorAssignment::getSubjectSectionId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<Long> idsOf(Long personId, Function<PreceptorAssignment, Long> target) {
        return preceptorAssignmentRepository.findByPersonId(personId).stream()
                .map(target)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }
}
