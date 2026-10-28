package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.StudentCreditBalance;
import com.red_escuela.backend.shared.domain.Money;

public record StudentCreditBalanceResponse(String currency, Money unallocatedCredit, Money reservedByPendingRefunds, Money availableCredit) {

    public static StudentCreditBalanceResponse from(StudentCreditBalance balance) {
        return new StudentCreditBalanceResponse(balance.currency().name(), balance.unallocatedCredit().value(), balance.reservedByPendingRefunds().value(),
                balance.availableCredit().value());
    }
}
