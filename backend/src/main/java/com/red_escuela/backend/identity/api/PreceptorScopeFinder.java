package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import com.red_escuela.backend.identity.repository.PreceptorAssignmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class PreceptorScopeFinder {

    private final PreceptorAssignmentRepository preceptorAssignmentRepository;

    public PreceptorScopeFinder(PreceptorAssignmentRepository preceptorAssignmentRepository) {
        this.preceptorAssignmentRepository = preceptorAssignmentRepository;
    }

    public Set<Long> assignedProgramIds(Long personId) {
        return idsOf(preceptorAssignmentRepository.findByPersonId(personId), PreceptorAssignment::getProgramId);
    }

    public Set<Long> assignedCourseIds(Long personId) {
        return idsOf(preceptorAssignmentRepository.findByPersonId(personId), PreceptorAssignment::getCourseId);
    }

    private Set<Long> idsOf(List<PreceptorAssignment> assignments, java.util.function.Function<PreceptorAssignment, Long> id) {
        return assignments.stream().map(id).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
}
