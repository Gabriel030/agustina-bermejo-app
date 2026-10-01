package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgramYearBillingTemplateRepository extends JpaRepository<ProgramYearBillingTemplate, Long> {

    Optional<ProgramYearBillingTemplate> findByProgramIdAndTermId(Long programId, Long termId);

    List<ProgramYearBillingTemplate> findAllByProgramId(Long programId);
}
