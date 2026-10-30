package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyInvoiceRunItemRepository extends JpaRepository<MonthlyInvoiceRunItem, Long> {

    List<MonthlyInvoiceRunItem> findByMonthlyInvoiceRunIdOrderByIdAsc(Long monthlyInvoiceRunId);

    List<MonthlyInvoiceRunItem> findByMonthlyInvoiceRunIdAndResultOrderByIdAsc(Long monthlyInvoiceRunId, MonthlyInvoiceRunItemResult result);

    long countByMonthlyInvoiceRunIdAndResult(Long monthlyInvoiceRunId, MonthlyInvoiceRunItemResult result);
}
