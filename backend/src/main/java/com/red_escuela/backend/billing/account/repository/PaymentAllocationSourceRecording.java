package com.red_escuela.backend.billing.account.repository;

import com.red_escuela.backend.billing.account.domain.AllocationSourceShare;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;

import java.util.List;

public interface PaymentAllocationSourceRecording {

    PaymentAllocation saveWithSources(PaymentAllocation allocation, List<AllocationSourceShare> shares);

    PaymentAllocation saveReversalWithSourcesOfTheReversedAllocation(PaymentAllocation reversal);
}
