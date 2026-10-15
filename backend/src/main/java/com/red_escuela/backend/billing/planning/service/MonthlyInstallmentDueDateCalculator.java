package com.red_escuela.backend.billing.planning.service;

import java.time.LocalDate;

public final class MonthlyInstallmentDueDateCalculator {

    public LocalDate calculateInstallmentDueDate(LocalDate issueDate, int dueDays) {
        return issueDate.plusDays(dueDays);
    }
}
