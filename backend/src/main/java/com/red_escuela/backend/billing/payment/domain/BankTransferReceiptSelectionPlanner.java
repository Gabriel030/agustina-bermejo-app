package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BankTransferReceiptSelectionPlanner {

    private static final Comparator<ReceiptInvoiceFact> ADMISSION_FIRST_THEN_OLDEST_DUE = Comparator
            .comparing((ReceiptInvoiceFact invoice) -> !invoice.programAdmission())
            .thenComparing(ReceiptInvoiceFact.OLDEST_DUE_FIRST);

    private BankTransferReceiptSelectionPlanner() {
    }

    public static List<ReceiptInvoiceFact> selectableInOrder(List<ReceiptInvoiceFact> openInvoicesOfAccount) {
        return openInvoicesOfAccount.stream()
                .filter(ReceiptInvoiceFact::isOpenToReceipt)
                .filter(invoice -> !invoice.programAdmission())
                .sorted(ReceiptInvoiceFact.OLDEST_DUE_FIRST)
                .toList();
    }

    public static List<PlannedCheckoutItem> planItems(List<ReceiptInvoiceFact> selectedInvoices, Money declaredAmount, LocalDate uploadDate,
                                                      LocalDate transferDate, ReceiptProtectionRule protectionRule) {
        Objects.requireNonNull(declaredAmount, "declaredAmount");
        List<ReceiptInvoiceFact> ordered = selectedInvoices.stream().sorted(ADMISSION_FIRST_THEN_OLDEST_DUE).toList();
        List<PlannedCheckoutItem> items = new ArrayList<>();
        Money remaining = declaredAmount;
        boolean stillCovering = true;
        int position = 1;
        for (ReceiptInvoiceFact invoice : ordered) {
            boolean covered = stillCovering && remaining.amount().compareTo(invoice.outstanding().amount()) >= 0;
            if (covered) {
                remaining = remaining.minus(invoice.outstanding());
            } else {
                stillCovering = false;
            }
            boolean protectedItem = covered
                    && protectionRule.protects(new ReceiptProtectionFacts(invoice.paymentStatus(), invoice.dueDate(), uploadDate, transferDate));
            items.add(new PlannedCheckoutItem(invoice.invoiceId(), position++, invoice.outstanding(), covered, protectedItem));
        }
        return List.copyOf(items);
    }
}
