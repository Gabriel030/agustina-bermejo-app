package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.ReservedAccountCredit;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PendingRefundReservedAccountCredit implements ReservedAccountCredit {
    private final RefundRepository refunds;

    public PendingRefundReservedAccountCredit(RefundRepository refunds) {
        this.refunds = refunds;
    }

    @Override
    public NonNegativeMoney reservedOn(Long studentCurrentAccountId, Currency currency) {
        List<Refund> pending = refunds.findByStudentCurrentAccountIdAndStatusIn(studentCurrentAccountId, RefundStatus.PENDING);
        return pending.stream().map(Refund::getAmount).reduce(NonNegativeMoney.zero(currency), NonNegativeMoney::plus);
    }
}
