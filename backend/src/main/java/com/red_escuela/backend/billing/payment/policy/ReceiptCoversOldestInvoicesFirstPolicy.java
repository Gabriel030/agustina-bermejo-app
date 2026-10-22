package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSelectionPlanner;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@PolicyRule(violationCodes = {"INVOICE_ALREADY_UNDER_REVIEW", "RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST"}, decisions = {"D-153", "D-260", "D-329"})
public final class ReceiptCoversOldestInvoicesFirstPolicy implements Policy<BankTransferReceiptRegistrationCheck> {

    @Override
    public String ruleName() {
        return "ReceiptCoversOldestInvoicesFirstPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(BankTransferReceiptRegistrationCheck check) {
        if (check.selectedInvoices().stream().anyMatch(ReceiptInvoiceFact::isUnderReview)) {
            return PolicyVerdict.deny(ruleName(), "INVOICE_ALREADY_UNDER_REVIEW", "La factura ya está en revisión por otro comprobante.");
        }
        List<ReceiptInvoiceFact> selectable = BankTransferReceiptSelectionPlanner.selectableInOrder(check.openInvoicesOfAccount()).stream()
                .filter(invoice -> !invoice.programAdmission()).toList();
        Set<Long> selectedIds = check.selectedInvoices().stream().filter(invoice -> !invoice.programAdmission()).map(ReceiptInvoiceFact::invoiceId)
                .collect(Collectors.toSet());
        Set<Long> oldestPrefixIds = selectable.stream().limit(selectedIds.size()).map(ReceiptInvoiceFact::invoiceId).collect(Collectors.toSet());
        return oldestPrefixIds.equals(selectedIds)
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST", "Elegí las facturas en orden, desde la más vencida.");
    }
}
