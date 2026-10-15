package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.AllocationSourceShare;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Map;

public interface PaymentAllocationSourceRecording {

    PaymentAllocation saveWithSources(PaymentAllocation allocation, List<AllocationSourceShare> shares);

    Map<Long, NonNegativeMoney> netSourcedByCreditEntry(Long studentCurrentAccountId);

    List<PaymentAllocation> findCurrentAllocationsFundedByPaymentNewestFirst(Long studentCurrentAccountId, Long paymentId);

    PaymentAllocation saveReversalWithSourcesOfTheReversedAllocation(PaymentAllocation reversal);
}
