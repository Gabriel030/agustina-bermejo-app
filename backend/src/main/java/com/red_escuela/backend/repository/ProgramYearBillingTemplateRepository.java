package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.ProgramYearBillingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProgramYearBillingTemplateRepository extends JpaRepository<ProgramYearBillingTemplate, Long> {

    Optional<ProgramYearBillingTemplate> findByProgramIdAndTermId(Long programId, Long termId);
}
