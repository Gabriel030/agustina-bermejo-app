package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramCohortRepository extends JpaRepository<ProgramCohort, Long> {

    List<ProgramCohort> findByTemplateId(Long templateId);

    Optional<ProgramCohort> findByTemplateIdAndIntake(Long templateId, CohortIntake intake);

    List<ProgramCohort> findByTemplate_ProgramId(Long programId);
}
