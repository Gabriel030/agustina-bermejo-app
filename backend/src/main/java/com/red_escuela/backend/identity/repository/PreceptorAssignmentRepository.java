package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PreceptorAssignmentRepository extends JpaRepository<PreceptorAssignment, Long> {

    List<PreceptorAssignment> findByPersonId(Long personId);

    List<PreceptorAssignment> findByProgramCohortId(Long programCohortId);

    List<PreceptorAssignment> findBySubjectSectionIdIn(Collection<Long> subjectSectionIds);

    List<PreceptorAssignment> findByCourseId(Long courseId);

    List<PreceptorAssignment> findBySubjectSectionIdOrderByIdAsc(Long subjectSectionId);

    List<PreceptorAssignment> findByCourseSectionIdOrderByIdAsc(Long courseSectionId);

    List<PreceptorAssignment> findByCourseSectionIdIn(Collection<Long> courseSectionIds);

    boolean existsByPersonIdAndSubjectSectionId(Long personId, Long subjectSectionId);

    boolean existsByPersonIdAndCourseSectionId(Long personId, Long courseSectionId);

    boolean existsByPersonIdAndCourseId(Long personId, Long courseId);

    boolean existsByPersonIdAndProgramCohortId(Long personId, Long programCohortId);
}
