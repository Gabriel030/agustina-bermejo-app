package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.policy.ReceiptProtectionCheck;
import com.red_escuela.backend.billing.payment.policy.ReceiptUploadedByDueDatePolicy;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-153")
@Tag("D-154")
@Tag("D-186")
@Tag("D-139")
class BankTransferReceiptSelectionPlannerTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);
    private static final ReceiptProtectionRule REAL_RULE = facts -> new ReceiptUploadedByDueDatePolicy().evaluateRule(new ReceiptProtectionCheck(facts)).allowed();

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static ReceiptInvoiceFact invoice(long id, InvoicePaymentStatus status, LocalDate dueDate, String outstanding) {
        return new ReceiptInvoiceFact(id, 7L, Currency.ARS, true, status, dueDate, ars(outstanding));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', value = {
            "uploaded on the due date;PENDING;2027-03-10;2027-03-10;true",
            "uploaded the day before;PENDING;2027-03-09;2027-03-09;true",
            "uploaded after the due date;PENDING;2027-03-11;2027-03-11;false",
            "uploaded after the due date with an earlier transfer;PENDING;2027-03-11;2027-03-09;false",
            "overdue invoice is never protected;OVERDUE;2027-03-09;2027-03-09;false",
            "transfer after the due date;PENDING;2027-03-09;2027-03-11;false",
            "transfer on the due date uploaded on the due date;PENDING;2027-03-10;2027-03-10;true"})
    void protectionByDatesFollowsTheTable(String description, InvoicePaymentStatus status, LocalDate uploadDate, LocalDate transferDate, boolean protectedExpected) {
        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(List.of(invoice(1, status, DUE, "80000.00")), ars("80000.00"), uploadDate,
                transferDate, REAL_RULE);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.coveredByDeclaredAmount()).isTrue();
            assertThat(item.protectedFromOverdue()).isEqualTo(protectedExpected);
        });
    }

    @Test
    void onlyTheInvoicesCoveredCompletelyAndInOrderAreProtected() {
        List<ReceiptInvoiceFact> selected = List.of(invoice(2, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 4, 10), "80000.00"),
                invoice(1, InvoicePaymentStatus.PENDING, DUE, "80000.00"));

        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(selected, ars("100000.00"), DUE.minusDays(1), DUE.minusDays(1), REAL_RULE);

        assertThat(items).extracting(PlannedCheckoutItem::invoiceId).containsExactly(1L, 2L);
        assertThat(items).extracting(PlannedCheckoutItem::position).containsExactly(1, 2);
        assertThat(items).extracting(PlannedCheckoutItem::coveredByDeclaredAmount).containsExactly(true, false);
        assertThat(items).extracting(PlannedCheckoutItem::protectedFromOverdue).containsExactly(true, false);
        assertThat(items).extracting(PlannedCheckoutItem::outstandingAtSelection).containsExactly(ars("80000.00"), ars("80000.00"));
    }

    @Test
    void anAmountThatCoversEveryInvoiceExactlyProtectsAll() {
        List<ReceiptInvoiceFact> selected = List.of(invoice(1, InvoicePaymentStatus.PENDING, DUE, "80000.00"),
                invoice(2, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 4, 10), "80000.00"));

        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(selected, ars("160000.00"), DUE.minusDays(1), DUE.minusDays(1), REAL_RULE);

        assertThat(items).extracting(PlannedCheckoutItem::protectedFromOverdue).containsExactly(true, true);
    }

    @Test
    void anAmountOneCentShortOfTheFirstInvoiceCoversNothing() {
        List<ReceiptInvoiceFact> selected = List.of(invoice(1, InvoicePaymentStatus.PENDING, DUE, "80000.00"),
                invoice(2, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 4, 10), "10.00"));

        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(selected, ars("79999.99"), DUE.minusDays(1), DUE.minusDays(1), REAL_RULE);

        assertThat(items).extracting(PlannedCheckoutItem::coveredByDeclaredAmount).containsExactly(false, false);
        assertThat(items).extracting(PlannedCheckoutItem::protectedFromOverdue).containsExactly(false, false);
    }

    @Test
    void anOverdueInvoiceInTheMiddleDoesNotStopTheCoverageOfTheNext() {
        List<ReceiptInvoiceFact> selected = List.of(invoice(1, InvoicePaymentStatus.OVERDUE, DUE, "80000.00"),
                invoice(2, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 4, 10), "80000.00"));

        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(selected, ars("160000.00"), DUE.plusDays(5), DUE.plusDays(5), REAL_RULE);

        assertThat(items).extracting(PlannedCheckoutItem::coveredByDeclaredAmount).containsExactly(true, true);
        assertThat(items).extracting(PlannedCheckoutItem::protectedFromOverdue).containsExactly(false, true);
    }

    @Test
    @Tag("D-329")
    void theProgramAdmissionInvoiceIsPlannedFirstSoTheDeclaredAmountCoversItBeforeAnOlderInstallment() {
        ReceiptInvoiceFact february = invoice(1, InvoicePaymentStatus.OVERDUE, LocalDate.of(2027, 2, 10), "50000.00");
        ReceiptInvoiceFact admission = new ReceiptInvoiceFact(2L, 7L, Currency.ARS, true, InvoicePaymentStatus.PENDING, DUE, ars("80000.00"), true);

        List<PlannedCheckoutItem> items = BankTransferReceiptSelectionPlanner.planItems(List.of(february, admission), ars("80000.00"), DUE.minusDays(1), DUE.minusDays(1),
                REAL_RULE);

        assertThat(items).extracting(PlannedCheckoutItem::invoiceId).containsExactly(2L, 1L);
        assertThat(items).extracting(PlannedCheckoutItem::position).containsExactly(1, 2);
        assertThat(items).extracting(PlannedCheckoutItem::coveredByDeclaredAmount).containsExactly(true, false);
        assertThat(items).extracting(PlannedCheckoutItem::protectedFromOverdue).containsExactly(true, false);
    }

    @Test
    void selectableInvoicesSkipThoseUnderReviewAndComeOldestFirst() {
        ReceiptInvoiceFact april = invoice(3, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 4, 10), "80000.00");
        ReceiptInvoiceFact march = invoice(2, InvoicePaymentStatus.OVERDUE, DUE, "80000.00");
        ReceiptInvoiceFact february = invoice(1, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, LocalDate.of(2027, 2, 10), "80000.00");

        List<ReceiptInvoiceFact> selectable = BankTransferReceiptSelectionPlanner.selectableInOrder(List.of(april, february, march));

        assertThat(selectable).containsExactly(march, april);
    }
}
