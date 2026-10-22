package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record PaymentVerificationCheck(Money verifiedAmount, LocalDate transferDate, String reviewNote, Currency accountCurrency, LocalDate today) {
}
