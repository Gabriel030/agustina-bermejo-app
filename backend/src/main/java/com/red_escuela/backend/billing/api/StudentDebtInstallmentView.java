package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;

public record StudentDebtInstallmentView(YearMonth period, LocalDate dueDate, Money amount, String status) {
}
