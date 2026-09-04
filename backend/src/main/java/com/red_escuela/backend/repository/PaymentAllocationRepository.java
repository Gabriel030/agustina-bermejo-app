package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByInvoiceId(Long invoiceId);
}
