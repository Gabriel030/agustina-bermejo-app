package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.ReservedAccountCredit;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class PendingRefundReservedAccountCredit implements ReservedAccountCredit {
    private static final Set<RefundStatus> RESERVING_STATUSES = Set.of(RefundStatus.REQUESTED, RefundStatus.APPROVED);

    private final RefundRepository refunds;

    public PendingRefundReservedAccountCredit(RefundRepository refunds) {
        this.refunds = refunds;
    }

    @Override
    public NonNegativeMoney reservedOn(Long studentCurrentAccountId, Currency currency) {
        List<Refund> pending = refunds.findByStudentCurrentAccountIdAndStatusIn(studentCurrentAccountId, RESERVING_STATUSES);
        return pending.stream().map(Refund::getAmount).reduce(NonNegativeMoney.zero(currency), NonNegativeMoney::plus);
    }
}
