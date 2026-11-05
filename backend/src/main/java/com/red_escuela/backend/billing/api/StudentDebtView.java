package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record StudentDebtView(Money totalDue, List<StudentDebtInstallmentView> installments) {

    public static StudentDebtView none() {
        return new StudentDebtView(Money.zero(Currency.ARS), List.of());
    }

    public int installmentCount() {
        return installments.size();
    }
}
