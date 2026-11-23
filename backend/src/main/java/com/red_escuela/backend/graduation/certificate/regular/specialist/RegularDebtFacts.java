package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.shared.domain.Money;

public record RegularDebtFacts(Money totalDue, int overdueInstallments, boolean hasDebt) {
}
