package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ProgramCohort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramCohortRepository extends JpaRepository<ProgramCohort, Long> {

    List<ProgramCohort> findByProgramIdOrderByOrderInYear(Long programId);

    Optional<ProgramCohort> findByProgramIdAndOrderInYear(Long programId, Integer orderInYear);
}
