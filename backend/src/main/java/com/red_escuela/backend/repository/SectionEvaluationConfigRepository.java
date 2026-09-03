package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.SectionEvaluationConfig;
import com.red_escuela.backend.entity.SectionEvaluationConfigStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionEvaluationConfigRepository extends JpaRepository<SectionEvaluationConfig, Long> {

    Optional<SectionEvaluationConfig> findBySubjectSectionId(Long subjectSectionId);

    List<SectionEvaluationConfig> findBySubjectSection_Instructors_InstructorId(Long instructorId);

    List<SectionEvaluationConfig> findByStatus(SectionEvaluationConfigStatus status);

    List<SectionEvaluationConfig> findByStatusIn(List<SectionEvaluationConfigStatus> statuses);
}
