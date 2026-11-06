package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.service.InvoiceQueryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class StudentOverdueDebtQuery {

    private final InvoiceQueryService invoiceQueryService;

    public StudentOverdueDebtQuery(InvoiceQueryService invoiceQueryService) {
        this.invoiceQueryService = invoiceQueryService;
    }

    public boolean hasOverdueDebt(Long studentId) {
        return invoiceQueryService.hasOverdueDebt(studentId);
    }
}
