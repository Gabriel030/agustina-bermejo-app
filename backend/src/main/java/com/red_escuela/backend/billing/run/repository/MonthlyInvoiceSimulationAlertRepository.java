package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyInvoiceSimulationAlertRepository extends JpaRepository<MonthlyInvoiceSimulationAlert, Long> {

    List<MonthlyInvoiceSimulationAlert> findByMonthlyInvoiceRunIdOrderByIdAsc(Long monthlyInvoiceRunId);
}
