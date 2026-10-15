package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PaymentAllocationSourceRepository extends Repository<PaymentAllocationSource, Long> {

    @Query("""
            select a.invoiceId, s from PaymentAllocationSource s join PaymentAllocation a on a.id = s.paymentAllocationId
            where a.invoiceId in :invoiceIds and a.reversesAllocationId is null
              and not exists (select 1 from PaymentAllocation r where r.reversesAllocationId = a.id)
            order by s.id
            """)
    List<Object[]> findCurrentSourcesWithTheirInvoiceId(@Param("invoiceIds") Collection<Long> invoiceIds);
}
