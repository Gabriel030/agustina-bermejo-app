package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record AllocationPlanContext(Long studentCurrentAccountId, Currency currency, NonNegativeMoney unallocatedCredit,
                                    List<OpenInvoiceView> openInvoicesInAllocationOrder, List<PlannedInvoiceAllocation> plan,
                                    Set<Long> programAdmissionInvoiceIdsBeingPaid) {

    public AllocationPlanContext {
        openInvoicesInAllocationOrder = List.copyOf(openInvoicesInAllocationOrder);
        plan = List.copyOf(plan);
        programAdmissionInvoiceIdsBeingPaid = Set.copyOf(programAdmissionInvoiceIdsBeingPaid);
    }

    public AllocationPlanContext(Long studentCurrentAccountId, Currency currency, NonNegativeMoney unallocatedCredit,
                                 List<OpenInvoiceView> openInvoicesInAllocationOrder, List<PlannedInvoiceAllocation> plan) {
        this(studentCurrentAccountId, currency, unallocatedCredit, openInvoicesInAllocationOrder, plan, Set.of());
    }

    public Optional<OpenInvoiceView> findOpenInvoice(Long invoiceId) {
        return openInvoicesInAllocationOrder.stream().filter(invoice -> invoice.invoiceId().equals(invoiceId)).findFirst();
    }
}
