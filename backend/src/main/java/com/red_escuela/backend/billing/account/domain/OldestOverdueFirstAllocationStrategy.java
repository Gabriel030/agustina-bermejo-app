package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OldestOverdueFirstAllocationStrategy implements AccountCreditAllocationStrategy {

    private static final Comparator<OpenInvoiceView> ALLOCATION_ORDER = Comparator
            .comparing(OpenInvoiceView::dueDate)
            .thenComparing(view -> view.documentType() == InvoiceDocumentType.INVOICE ? 0 : 1)
            .thenComparing(OpenInvoiceView::issueDate)
            .thenComparing(OpenInvoiceView::invoiceId);

    @Override
    public List<OpenInvoiceView> sortInAllocationOrder(List<OpenInvoiceView> openInvoices) {
        Objects.requireNonNull(openInvoices, "openInvoices");
        return openInvoices.stream().sorted(ALLOCATION_ORDER).toList();
    }

    @Override
    public List<PlannedInvoiceAllocation> planFullInvoiceAllocations(NonNegativeMoney unallocatedCredit, List<OpenInvoiceView> openInvoices) {
        Objects.requireNonNull(unallocatedCredit, "unallocatedCredit");
        Money remaining = unallocatedCredit.value();
        List<PlannedInvoiceAllocation> plan = new ArrayList<>();
        for (OpenInvoiceView invoice : sortInAllocationOrder(openInvoices)) {
            Money outstanding = invoice.outstandingAmount().value();
            if (outstanding.isZero()) {
                continue;
            }
            if (outstanding.isGreaterThan(remaining)) {
                break;
            }
            plan.add(new PlannedInvoiceAllocation(invoice.invoiceId(), invoice.outstandingAmount()));
            remaining = remaining.minus(outstanding);
        }
        return List.copyOf(plan);
    }
}
