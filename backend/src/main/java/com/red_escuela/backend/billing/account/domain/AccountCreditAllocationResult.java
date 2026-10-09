package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;

import java.util.List;

public record AccountCreditAllocationResult(List<PaymentAllocation> allocations, List<Invoice> settledInvoices) {

    public AccountCreditAllocationResult {
        allocations = List.copyOf(allocations);
        settledInvoices = List.copyOf(settledInvoices);
    }

    public static AccountCreditAllocationResult nothingAllocated() {
        return new AccountCreditAllocationResult(List.of(), List.of());
    }

    public boolean isEmpty() {
        return allocations.isEmpty();
    }
}
