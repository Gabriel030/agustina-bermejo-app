package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProgramCohortRepository extends JpaRepository<ProgramCohort, Long> {

    List<ProgramCohort> findByTemplateId(Long templateId);

    Optional<ProgramCohort> findByTemplateIdAndIntake(Long templateId, CohortIntake intake);

    List<ProgramCohort> findByTemplate_ProgramId(Long programId);

    @Query(value = "select created_by_id from program_cohort where id = :id", nativeQuery = true)
    Long findCreatedByIdById(@Param("id") Long id);
}
