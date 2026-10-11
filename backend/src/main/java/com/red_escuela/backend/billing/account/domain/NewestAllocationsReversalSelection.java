package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NewestAllocationsReversalSelection {

    private NewestAllocationsReversalSelection() {
    }

    public static List<PaymentAllocation> selectAllocationsToReverse(List<PaymentAllocation> allocationsNewestFirst, NonNegativeMoney unallocatedCredit,
                                                                    NonNegativeMoney creditToRemove) {
        Objects.requireNonNull(allocationsNewestFirst, "allocationsNewestFirst");
        Objects.requireNonNull(unallocatedCredit, "unallocatedCredit");
        Objects.requireNonNull(creditToRemove, "creditToRemove");
        List<PaymentAllocation> selected = new ArrayList<>();
        NonNegativeMoney creditAfterReversals = unallocatedCredit;
        for (PaymentAllocation current : allocationsNewestFirst) {
            if (!creditToRemove.value().isGreaterThan(creditAfterReversals.value())) {
                break;
            }
            selected.add(current);
            creditAfterReversals = creditAfterReversals.plus(current.getAmount());
        }
        return List.copyOf(selected);
    }
}
