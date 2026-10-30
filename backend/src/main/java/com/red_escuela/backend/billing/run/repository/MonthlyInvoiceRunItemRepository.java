package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MonthlyInvoiceRunItemRepository extends JpaRepository<MonthlyInvoiceRunItem, Long> {

    List<MonthlyInvoiceRunItem> findByMonthlyInvoiceRunIdOrderByIdAsc(Long monthlyInvoiceRunId);

    List<MonthlyInvoiceRunItem> findByMonthlyInvoiceRunIdAndResultOrderByIdAsc(Long monthlyInvoiceRunId, MonthlyInvoiceRunItemResult result);

    long countByMonthlyInvoiceRunIdAndResult(Long monthlyInvoiceRunId, MonthlyInvoiceRunItemResult result);

    @Query("select item.id from MonthlyInvoiceRunItem item where item.result = com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult.FAILED "
            + "and item.nextRetryAt <= :now order by item.nextRetryAt, item.id")
    List<Long> findDueFailedItemIds(@Param("now") LocalDateTime now, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from MonthlyInvoiceRunItem item where item.id = :id")
    Optional<MonthlyInvoiceRunItem> findByIdForUpdate(@Param("id") Long id);
}
