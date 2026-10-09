package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Optional;

public record AllocationPlanContext(Long studentCurrentAccountId, Currency currency, NonNegativeMoney unallocatedCredit,
                                    List<OpenInvoiceView> openInvoicesInAllocationOrder, List<PlannedInvoiceAllocation> plan) {

    public AllocationPlanContext {
        openInvoicesInAllocationOrder = List.copyOf(openInvoicesInAllocationOrder);
        plan = List.copyOf(plan);
    }

    public Optional<OpenInvoiceView> findOpenInvoice(Long invoiceId) {
        return openInvoicesInAllocationOrder.stream().filter(invoice -> invoice.invoiceId().equals(invoiceId)).findFirst();
    }
}
