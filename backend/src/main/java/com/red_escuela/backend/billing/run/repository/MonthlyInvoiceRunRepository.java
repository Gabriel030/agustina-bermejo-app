package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MonthlyInvoiceRunRepository extends JpaRepository<MonthlyInvoiceRun, Long> {

    long countByStatusIn(Collection<MonthlyInvoiceRunStatus> statuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MonthlyInvoiceRun r where r.id = :id")
    java.util.Optional<MonthlyInvoiceRun> findByIdForUpdate(@Param("id") Long id);

    boolean existsByPeriodAndModeAndStatusNot(LocalDate period, com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode mode, MonthlyInvoiceRunStatus status);

    Page<MonthlyInvoiceRun> findByPeriod(LocalDate period, Pageable page);

    List<MonthlyInvoiceRun> findByPeriodOrderByIdAsc(LocalDate period);

    List<MonthlyInvoiceRun> findByPeriodAndStatusAndStartedAtBefore(LocalDate period, MonthlyInvoiceRunStatus status, LocalDateTime startedBefore);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MonthlyInvoiceRun r where r.mode = com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode.SIMULATION and r.period = :period and r.businessDate = :businessDate")
    void deleteSimulationsOfPeriodAndBusinessDate(@Param("period") LocalDate period, @Param("businessDate") LocalDate businessDate);

    org.springframework.data.domain.Page<MonthlyInvoiceRun> findByMode(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode mode, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<MonthlyInvoiceRun> findByPeriodAndMode(LocalDate period, com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode mode, org.springframework.data.domain.Pageable pageable);
}
