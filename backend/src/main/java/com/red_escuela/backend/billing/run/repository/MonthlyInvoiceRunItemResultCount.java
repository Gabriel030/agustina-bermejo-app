package com.red_escuela.backend.billing.run.repository;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;

public interface MonthlyInvoiceRunItemResultCount {
    Long getRunId();

    MonthlyInvoiceRunItemResult getResult();

    long getTotal();
}
