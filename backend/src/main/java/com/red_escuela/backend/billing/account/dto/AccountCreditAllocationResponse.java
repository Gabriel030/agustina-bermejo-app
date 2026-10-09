package com.red_escuela.backend.billing.account.dto;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record AccountCreditAllocationResponse(List<AllocationItem> allocations) {

    public static AccountCreditAllocationResponse from(List<AccountCreditAllocationResult> results) {
        return new AccountCreditAllocationResponse(results.stream().flatMap(result -> result.allocations().stream()).map(AllocationItem::from).toList());
    }

    public record AllocationItem(Long allocationId, Long invoiceId, Money amount, LocalDate effectiveDate, LocalDate allocatedOn) {

        static AllocationItem from(PaymentAllocation allocation) {
            return new AllocationItem(allocation.getId(), allocation.getInvoiceId(), allocation.getAmount().value(), allocation.getEffectiveDate(),
                    allocation.getAllocatedOn());
        }
    }
}
