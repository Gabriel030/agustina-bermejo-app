package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentAllocationRepository extends Repository<PaymentAllocation, Long> {

    PaymentAllocation save(PaymentAllocation allocation);

    Optional<PaymentAllocation> findById(Long id);

    List<PaymentAllocation> findByInvoiceId(Long invoiceId);

    boolean existsByReversesAllocationId(Long reversesAllocationId);

    @Query("""
            select a from PaymentAllocation a
            where a.studentCurrentAccountId = :accountId and a.reversesAllocationId is null
              and not exists (select 1 from PaymentAllocation r where r.reversesAllocationId = a.id)
            order by a.allocatedOn desc, a.id desc
            """)
    List<PaymentAllocation> findCurrentAllocationsByAccountNewestFirst(@Param("accountId") Long studentCurrentAccountId);

    @Query("""
            select a from PaymentAllocation a
            where a.invoiceId = :invoiceId and a.reversesAllocationId is null
              and not exists (select 1 from PaymentAllocation r where r.reversesAllocationId = a.id)
            """)
    Optional<PaymentAllocation> findCurrentAllocationByInvoiceId(@Param("invoiceId") Long invoiceId);
}
