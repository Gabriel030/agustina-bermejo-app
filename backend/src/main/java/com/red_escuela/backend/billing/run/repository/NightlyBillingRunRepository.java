package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NightlyBillingRunRepository extends JpaRepository<NightlyBillingRun, Long> {

    boolean existsByBusinessDate(LocalDate businessDate);

    Optional<NightlyBillingRun> findFirstByOrderByBusinessDateAsc();

    Optional<NightlyBillingRun> findByBusinessDate(LocalDate businessDate);

    @Query("select r from NightlyBillingRun r where r.businessDate between :from and :to order by r.businessDate desc")
    List<NightlyBillingRun> findByBusinessDateBetweenNewestFirst(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
