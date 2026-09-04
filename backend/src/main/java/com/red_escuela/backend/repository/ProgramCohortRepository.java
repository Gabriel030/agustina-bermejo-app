package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.CohortIntake;
import com.red_escuela.backend.entity.ProgramCohort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramCohortRepository extends JpaRepository<ProgramCohort, Long> {

    List<ProgramCohort> findByTemplateId(Long templateId);

    Optional<ProgramCohort> findByTemplateIdAndIntake(Long templateId, CohortIntake intake);
}
