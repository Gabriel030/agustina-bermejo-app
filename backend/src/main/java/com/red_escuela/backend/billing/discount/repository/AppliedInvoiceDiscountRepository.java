package com.red_escuela.backend.billing.discount.repository;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppliedInvoiceDiscountRepository extends JpaRepository<InvoiceDiscount, Long> {

    boolean existsByStudentDiscountIdAndDiscountTypeAndConsumptionReleasedAtIsNull(Long studentDiscountId, DiscountType discountType);
}
