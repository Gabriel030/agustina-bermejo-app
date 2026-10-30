package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyInvoiceRunItemAttemptRepository extends JpaRepository<MonthlyInvoiceRunItemAttempt, Long> {

    List<MonthlyInvoiceRunItemAttempt> findByMonthlyInvoiceRunItemIdOrderByAttemptNumberAsc(Long monthlyInvoiceRunItemId);
}
