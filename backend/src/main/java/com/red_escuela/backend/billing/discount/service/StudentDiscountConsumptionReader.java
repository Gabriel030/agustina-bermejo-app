package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.repository.AppliedInvoiceDiscountRepository;
import org.springframework.stereotype.Component;

@Component
public class StudentDiscountConsumptionReader {
    private final AppliedInvoiceDiscountRepository repository;

    public StudentDiscountConsumptionReader(AppliedInvoiceDiscountRepository repository) {
        this.repository = repository;
    }

    public boolean isAlreadyConsumed(StudentDiscount discount) {
        return repository.existsByStudentDiscountIdAndDiscountTypeAndConsumptionReleasedAtIsNull(discount.getId(), discount.getDiscountType());
    }
}
