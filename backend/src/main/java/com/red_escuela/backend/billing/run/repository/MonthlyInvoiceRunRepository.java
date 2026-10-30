package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import org.springframework.data.jpa.repository.JpaRepository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MonthlyInvoiceRunRepository extends JpaRepository<MonthlyInvoiceRun, Long> {

    List<MonthlyInvoiceRun> findByPeriodOrderByIdAsc(LocalDate period);

    List<MonthlyInvoiceRun> findByPeriodAndStatusAndStartedAtBefore(LocalDate period, MonthlyInvoiceRunStatus status, LocalDateTime startedBefore);
}
