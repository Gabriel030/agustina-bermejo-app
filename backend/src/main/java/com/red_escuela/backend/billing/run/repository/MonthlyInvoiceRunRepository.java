package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MonthlyInvoiceRunRepository extends JpaRepository<MonthlyInvoiceRun, Long> {

    List<MonthlyInvoiceRun> findByPeriodOrderByIdAsc(LocalDate period);
}
