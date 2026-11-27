package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProgramCohortRepository extends JpaRepository<ProgramCohort, Long> {

    List<ProgramCohort> findByTemplateId(Long templateId);

    List<ProgramCohort> findByTemplateIdIn(Collection<Long> templateIds);

    Optional<ProgramCohort> findByTemplateIdAndIntake(Long templateId, CohortIntake intake);

    Optional<ProgramCohort> findByProgramIdAndTermIdAndIntake(Long programId, Long termId, CohortIntake intake);

    @Query(value = "select created_by_id from program_cohort where id = :id", nativeQuery = true)
    Long findCreatedByIdById(@Param("id") Long id);
}
