package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProgramAdmissionInvoicePaidFirstAllocationStrategy implements AccountCreditAllocationStrategy {

    private final AccountCreditAllocationStrategy baseOrder;
    private final Set<Long> programAdmissionInvoiceIdsBeingPaid;

    public ProgramAdmissionInvoicePaidFirstAllocationStrategy(AccountCreditAllocationStrategy baseOrder, Set<Long> programAdmissionInvoiceIdsBeingPaid) {
        this.baseOrder = Objects.requireNonNull(baseOrder, "baseOrder");
        this.programAdmissionInvoiceIdsBeingPaid = Set.copyOf(Objects.requireNonNull(programAdmissionInvoiceIdsBeingPaid, "programAdmissionInvoiceIdsBeingPaid"));
    }

    public static Set<Long> programAdmissionInvoiceIdsFullyCoveredByCredit(AccountCreditAllocationStrategy baseOrder, Set<Long> programAdmissionInvoiceIds,
                                                                          NonNegativeMoney credit, List<OpenInvoiceView> openInvoices) {
        Set<Long> covered = new LinkedHashSet<>();
        Money remaining = credit.value();
        for (OpenInvoiceView invoice : baseOrder.sortInAllocationOrder(openInvoices)) {
            if (!programAdmissionInvoiceIds.contains(invoice.invoiceId()) || invoice.outstandingAmount().value().isZero()) {
                continue;
            }
            if (invoice.outstandingAmount().value().isGreaterThan(remaining)) {
                break;
            }
            remaining = remaining.minus(invoice.outstandingAmount().value());
            covered.add(invoice.invoiceId());
        }
        return Set.copyOf(covered);
    }

    @Override
    public List<OpenInvoiceView> sortInAllocationOrder(List<OpenInvoiceView> openInvoices) {
        List<OpenInvoiceView> ordered = baseOrder.sortInAllocationOrder(openInvoices);
        List<OpenInvoiceView> result = new ArrayList<>(ordered.stream().filter(this::isProgramAdmissionBeingPaid).toList());
        result.addAll(ordered.stream().filter(invoice -> !isProgramAdmissionBeingPaid(invoice)).toList());
        return List.copyOf(result);
    }

    @Override
    public List<PlannedInvoiceAllocation> planFullInvoiceAllocations(NonNegativeMoney unallocatedCredit, List<OpenInvoiceView> openInvoices) {
        return OldestOverdueFirstAllocationStrategy.planFullInvoiceAllocationsInGivenOrder(unallocatedCredit, sortInAllocationOrder(openInvoices));
    }

    private boolean isProgramAdmissionBeingPaid(OpenInvoiceView invoice) {
        return programAdmissionInvoiceIdsBeingPaid.contains(invoice.invoiceId());
    }
}
