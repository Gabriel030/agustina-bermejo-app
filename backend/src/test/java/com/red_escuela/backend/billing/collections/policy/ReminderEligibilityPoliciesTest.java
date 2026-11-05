package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFacts;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderEligibilityPoliciesTest {
    private final ReminderEligibleInvoicePolicy eligible = new ReminderEligibleInvoicePolicy();
    private final ReceiptUnderReviewSuppressesReminderPolicy receiptUnderReview = new ReceiptUnderReviewSuppressesReminderPolicy();
    private final PendingCancellationSuppressesReminderPolicy pendingCancellation = new PendingCancellationSuppressesReminderPolicy();

    private static ReminderInvoiceFact fact(InvoiceDocumentType documentType, InvoiceType type, InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus,
                                            boolean receiptUnderReview, boolean openCancellationRequest) {
        return ReminderInvoiceFacts.fact(1L, null, documentType, type, documentStatus, paymentStatus, "2027-03-01", "2027-03-11", null, receiptUnderReview, openCancellationRequest);
    }

    private static ReminderInvoiceFact invoice(InvoiceType type, InvoiceDocumentStatus documentStatus, InvoicePaymentStatus paymentStatus) {
        return fact(InvoiceDocumentType.INVOICE, type, documentStatus, paymentStatus, false, false);
    }

    @ParameterizedTest
    @EnumSource(InvoiceType.class)
    @Tag("D-354")
    @Tag("D-242")
    @Tag("D-149")
    void onlyIssuedInstallmentsAndEnrollmentFeesAreRemindable(InvoiceType type) {
        boolean remindableType = Set.of(InvoiceType.MONTHLY_INSTALLMENT, InvoiceType.ENROLLMENT_FEE).contains(type);

        for (InvoiceDocumentStatus documentStatus : InvoiceDocumentStatus.values()) {
            for (InvoicePaymentStatus paymentStatus : InvoicePaymentStatus.values()) {
                boolean expected = remindableType && documentStatus == InvoiceDocumentStatus.ISSUED
                        && (paymentStatus == InvoicePaymentStatus.PENDING || paymentStatus == InvoicePaymentStatus.OVERDUE);

                assertThat(eligible.evaluateRule(invoice(type, documentStatus, paymentStatus)).allowed())
                        .as("%s %s %s", type, documentStatus, paymentStatus).isEqualTo(expected);
            }
        }
    }

    @Test
    @Tag("D-354")
    @Tag("D-149")
    void aLateFeeDebitNoteIsNeverRemindedOnItsOwn() {
        ReminderInvoiceFact debitNote = fact(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, false, false);
        ReminderInvoiceFact debitNoteDisguisedAsInstallment = fact(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, false, false);

        assertThat(eligible.evaluateRule(debitNote).allowed()).isFalse();
        assertThat(eligible.evaluateRule(debitNoteDisguisedAsInstallment).allowed()).isFalse();
    }

    @Test
    @Tag("D-354")
    @Tag("D-052")
    @Tag("D-131")
    void draftsAndInvoicesAwaitingPaymentAreNotRemindable() {
        assertThat(eligible.evaluateRule(invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.DRAFT, InvoicePaymentStatus.PENDING)).allowed()).isFalse();
        assertThat(eligible.evaluateRule(invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.AWAITING_PAYMENT, InvoicePaymentStatus.PENDING)).allowed()).isFalse();
        assertThat(eligible.evaluateRule(invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.DISCARDED, InvoicePaymentStatus.PENDING)).allowed()).isFalse();
    }

    @Test
    @Tag("D-354")
    @Tag("D-346")
    @Tag("D-247")
    void everyVerdictCarriesTheNameOfItsPolicy() {
        ReminderInvoiceFact blocked = fact(InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE, InvoiceDocumentStatus.DRAFT, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, true, true);

        assertThat(eligible.evaluateRule(blocked).policy()).isEqualTo("ReminderEligibleInvoicePolicy");
        assertThat(receiptUnderReview.evaluateRule(blocked).policy()).isEqualTo("ReceiptUnderReviewSuppressesReminderPolicy");
        assertThat(pendingCancellation.evaluateRule(blocked).policy()).isEqualTo("PendingCancellationSuppressesReminderPolicy");
        assertThat(eligible.ruleName()).isEqualTo("ReminderEligibleInvoicePolicy");
        assertThat(receiptUnderReview.ruleName()).isEqualTo("ReceiptUnderReviewSuppressesReminderPolicy");
        assertThat(pendingCancellation.ruleName()).isEqualTo("PendingCancellationSuppressesReminderPolicy");
    }

    @Test
    @Tag("D-354")
    void aDeniedVerdictCarriesTheRemindableCode() {
        PolicyVerdict verdict = eligible.evaluateRule(invoice(InvoiceType.EXAM_FEE, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("INVOICE_NOT_REMINDABLE");
    }

    @Test
    @Tag("D-346")
    @Tag("D-186")
    void receiptUnderReviewSuppresses() {
        ReminderInvoiceFact overdueWithReceipt = fact(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, true, false);
        ReminderInvoiceFact overdueWithoutReceipt = fact(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.OVERDUE, false, false);

        assertThat(receiptUnderReview.evaluateRule(overdueWithReceipt).allowed()).isFalse();
        assertThat(receiptUnderReview.evaluateRule(overdueWithReceipt).code()).isEqualTo("RECEIPT_UNDER_REVIEW");
        assertThat(receiptUnderReview.evaluateRule(overdueWithoutReceipt).allowed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(InvoicePaymentStatus.class)
    @Tag("D-346")
    void paymentUnderReviewSuppresses(InvoicePaymentStatus status) {
        ReminderInvoiceFact fact = invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, status);

        assertThat(receiptUnderReview.evaluateRule(fact).allowed()).isEqualTo(status != InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    @Tag("D-247")
    @Tag("D-354")
    void pendingCancellationSuppresses() {
        ReminderInvoiceFact withOpenRequest = fact(InvoiceDocumentType.INVOICE, InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING, false, true);
        ReminderInvoiceFact withoutOpenRequest = invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING);

        assertThat(pendingCancellation.evaluateRule(withOpenRequest).allowed()).isFalse();
        assertThat(pendingCancellation.evaluateRule(withOpenRequest).code()).isEqualTo("PENDING_CANCELLATION_REQUEST");
        assertThat(pendingCancellation.evaluateRule(withoutOpenRequest).allowed()).isTrue();
    }

    @Test
    @Tag("D-354")
    void anInvoiceInsideTheGraceWindowIsStillRemindable() {
        ReminderInvoiceFact pastDueButNotYetOverdue = invoice(InvoiceType.MONTHLY_INSTALLMENT, InvoiceDocumentStatus.ISSUED, InvoicePaymentStatus.PENDING);

        assertThat(eligible.evaluateRule(pastDueButNotYetOverdue).allowed()).isTrue();
    }
}
