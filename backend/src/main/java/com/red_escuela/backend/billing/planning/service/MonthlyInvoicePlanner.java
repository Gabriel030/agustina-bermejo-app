package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.MonthlyInvoicePlan;

public interface MonthlyInvoicePlanner<F> {
    MonthlyInvoicePlan planInvoicesForMonth(F facts);
}
