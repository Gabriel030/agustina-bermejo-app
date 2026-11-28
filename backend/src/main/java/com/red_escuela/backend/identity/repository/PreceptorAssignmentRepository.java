package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PreceptorAssignmentRepository extends JpaRepository<PreceptorAssignment, Long> {

    List<PreceptorAssignment> findByPersonId(Long personId);

    List<PreceptorAssignment> findByProgramCohortId(Long programCohortId);

    List<PreceptorAssignment> findBySubjectSectionIdIn(Collection<Long> subjectSectionIds);

    boolean existsByPersonIdAndProgramCohortId(Long personId, Long programCohortId);
}
