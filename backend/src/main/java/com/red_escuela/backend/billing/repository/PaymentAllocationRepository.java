package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository("legacyPaymentAllocationRepository")
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByInvoiceId(Long invoiceId);

    @Query("select a from LegacyPaymentAllocation a join fetch a.payment p join fetch p.registeredBy where a.invoice.id in :invoiceIds")
    List<PaymentAllocation> findByInvoiceIdInWithPayments(@Param("invoiceIds") Collection<Long> invoiceIds);
}
