package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionEvaluationConfigRepository extends JpaRepository<SectionEvaluationConfig, Long> {

    Optional<SectionEvaluationConfig> findBySubjectSectionId(Long subjectSectionId);

    Optional<SectionEvaluationConfig> findByCourseSectionId(Long courseSectionId);

    List<SectionEvaluationConfig> findBySubjectSection_Instructors_InstructorId(Long instructorId);

    List<SectionEvaluationConfig> findByCourseSection_Instructors_InstructorId(Long instructorId);

    List<SectionEvaluationConfig> findByStatusAndSubjectSectionIsNotNull(SectionEvaluationConfigStatus status);

    List<SectionEvaluationConfig> findByStatusAndCourseSectionIsNotNull(SectionEvaluationConfigStatus status);

    List<SectionEvaluationConfig> findByStatusIn(List<SectionEvaluationConfigStatus> statuses);

    @org.springframework.data.jpa.repository.Query("""
            select c from SectionEvaluationConfig c
            where c.tp1DueDate = :date or c.tp2DueDate = :date or c.tp3DueDate = :date
               or c.exam1Date = :date or c.exam2Date = :date or c.exam3Date = :date or c.exam4Date = :date
               or c.finalCall1Date = :date or c.finalCall2Date = :date
            """)
    java.util.List<SectionEvaluationConfig> findWithAnyEvaluationDateOn(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date);
}
