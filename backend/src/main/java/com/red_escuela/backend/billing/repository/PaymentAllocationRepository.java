package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("legacyPaymentAllocationRepository")
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByInvoiceId(Long invoiceId);

}
