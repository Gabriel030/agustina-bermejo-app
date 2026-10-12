package com.red_escuela.backend.billing.account.policy;

import com.red_escuela.backend.billing.account.domain.OldestOverdueFirstAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.List;
import java.util.Set;

@PolicyRule(violationCodes = "ALLOCATION_ORDER_VIOLATED", decisions = {"D-089", "D-128", "D-139", "D-140", "D-329"})
public final class OldestOverdueFirstAllocationOrderPolicy implements Policy<AllocationPlanContext> {

    private static final OldestOverdueFirstAllocationStrategy OLDEST_FIRST = new OldestOverdueFirstAllocationStrategy();

    @Override
    public String ruleName() {
        return "OldestOverdueFirstAllocationOrderPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AllocationPlanContext context) {
        List<OpenInvoiceView> ordered = context.openInvoicesInAllocationOrder().stream()
                .filter(invoice -> !invoice.outstandingAmount().value().isZero()).toList();
        if (!onlyDeclaredProgramAdmissionInvoicesWereAdvanced(ordered, context.programAdmissionInvoiceIdsBeingPaid())) {
            return violated();
        }
        int plannedCount = context.plan().size();
        if (plannedCount > ordered.size()) {
            return violated();
        }
        Money remaining = context.unallocatedCredit().value();
        for (int position = 0; position < plannedCount; position++) {
            OpenInvoiceView expected = ordered.get(position);
            if (!expected.invoiceId().equals(context.plan().get(position).invoiceId())) {
                return violated();
            }
            remaining = remaining.minus(expected.outstandingAmount().value());
        }
        boolean stoppedAtTheFirstInvoiceThatDoesNotFit = plannedCount == ordered.size()
                || ordered.get(plannedCount).outstandingAmount().value().isGreaterThan(remaining);
        return stoppedAtTheFirstInvoiceThatDoesNotFit && !remaining.isNegative() ? PolicyVerdict.allow(ruleName()) : violated();
    }

    private boolean onlyDeclaredProgramAdmissionInvoicesWereAdvanced(List<OpenInvoiceView> ordered, Set<Long> declared) {
        boolean seenUndeclared = false;
        for (OpenInvoiceView invoice : ordered) {
            boolean isDeclared = declared.contains(invoice.invoiceId());
            if (isDeclared && seenUndeclared) {
                return false;
            }
            seenUndeclared = seenUndeclared || !isDeclared;
        }
        List<OpenInvoiceView> declaredInvoices = ordered.stream().filter(invoice -> declared.contains(invoice.invoiceId())).toList();
        List<OpenInvoiceView> otherInvoices = ordered.stream().filter(invoice -> !declared.contains(invoice.invoiceId())).toList();
        return declaredInvoices.equals(OLDEST_FIRST.sortInAllocationOrder(declaredInvoices)) && otherInvoices.equals(OLDEST_FIRST.sortInAllocationOrder(otherInvoices));
    }

    private PolicyVerdict violated() {
        return PolicyVerdict.deny(ruleName(), "ALLOCATION_ORDER_VIOLATED",
                "La imputación debe ir de la factura más vencida a la más nueva y detenerse en la primera que no alcanza");
    }
}
