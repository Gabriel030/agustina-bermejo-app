package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProgramYearBillingTemplateRepository extends JpaRepository<ProgramYearBillingTemplate, Long> {

    Optional<ProgramYearBillingTemplate> findByProgramIdAndTermId(Long programId, Long termId);

    List<ProgramYearBillingTemplate> findAllByProgramId(Long programId);

    List<ProgramYearBillingTemplate> findAllByTermId(Long termId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ProgramYearBillingTemplate t where t.id = :id")
    Optional<ProgramYearBillingTemplate> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "select id from program_year_billing_template where id = :id for share", nativeQuery = true)
    Optional<Long> lockForShare(@Param("id") Long id);

    @Query(value = "select program_id from program_year_billing_template where id = :id", nativeQuery = true)
    Long findProgramIdById(@Param("id") Long id);

    @Query(value = "select term_id from program_year_billing_template where id = :id", nativeQuery = true)
    Long findTermIdById(@Param("id") Long id);

    @Query(value = "select created_by_id from program_year_billing_template where id = :id", nativeQuery = true)
    Long findCreatedByIdById(@Param("id") Long id);
}
