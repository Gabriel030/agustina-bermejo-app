package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.service.InvoiceQueryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class StudentOutstandingDebtQuery {

    private final InvoiceQueryService invoiceQueryService;

    public StudentOutstandingDebtQuery(InvoiceQueryService invoiceQueryService) {
        this.invoiceQueryService = invoiceQueryService;
    }

    public boolean hasOutstandingDebt(Long studentId) {
        return invoiceQueryService.hasDebt(studentId);
    }
}
