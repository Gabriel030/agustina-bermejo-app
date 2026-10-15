package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CashPaymentRegistrationResult(Long paymentId, Long studentCurrentAccountId, NonNegativeMoney amount, LocalDate paidOn, LocalDateTime registeredAt,
                                            List<AllocatedInvoice> allocatedInvoices, List<AllocatedInvoice> allocatedFromRemainingCredit,
                                            NonNegativeMoney unallocatedCredit) {

    public CashPaymentRegistrationResult {
        allocatedInvoices = List.copyOf(allocatedInvoices);
        allocatedFromRemainingCredit = allocatedFromRemainingCredit == null ? List.of() : List.copyOf(allocatedFromRemainingCredit);
    }

    public record AllocatedInvoice(Long invoiceId, NonNegativeMoney amount) {
    }
}
