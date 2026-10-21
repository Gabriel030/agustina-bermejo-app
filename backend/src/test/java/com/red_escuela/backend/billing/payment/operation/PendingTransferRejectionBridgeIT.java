package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PendingTransferRejectionBridgeIT extends AbstractPendingTransferBridgeIT {

    @Test
    @Tag("D-154")
    @Tag("D-208")
    void rejectionReleasesReviewToPendingBeforeDueDate() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-REJECT-1");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");

        BankTransferReceipt rejected = reject(submitted, "No coincide el monto");

        assertThat(rejected.getStatus().name()).isEqualTo("REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(dueDateOfInvoice(march)).isEqualTo(MARCH_DUE);
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "REJECTED").containsEntry("rejection_reason", "No coincide el monto");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, debtor.accountId()))
                .isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-154")
    @Tag("D-208")
    void rejectionAfterDueDateReturnsToOverdueWithOriginalDueDate() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-REJECT-2");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 15));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 15));
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 18));

        reject(submitted, "Comprobante ilegible");

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        assertThat(dueDateOfInvoice(march)).isEqualTo(MARCH_DUE);
    }

    @Test
    @Tag("D-208")
    @Tag("D-261")
    void rejectionSkipsItemsNoLongerUnderReview() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        Long may = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 5, 10));
        BankTransferReceiptSubmissionResult first = submitOn(debtor, List.of(march, april), "160000.00", "OP-PAID-IN-THE-MIDDLE-1");
        BankTransferReceiptSubmissionResult second = submitOn(debtor, List.of(may), "80000.00", "OP-PAID-IN-THE-MIDDLE-2");
        approve(second);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAYMENT_UNDER_REVIEW");

        BankTransferReceipt rejected = reject(first, "El pago ya se aplicó por otro comprobante");

        assertThat(rejected.getStatus().name()).isEqualTo("REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(paymentStatusOfInvoice(may)).isEqualTo("PENDING");
        assertThat(paymentRow(first.paymentId())).containsEntry("status", "REJECTED");
        assertThat(paymentRow(second.paymentId())).containsEntry("status", "APPROVED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-270")
    void theAccountHolderIsNotifiedOfTheRejectionEvenWhenStaffUploadedTheReceipt() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-REJECT-NOTIFY");

        reject(submitted, "Falta el titular");

        assertThat(rejectedEvents()).singleElement().satisfies(event -> {
            assertThat(event.payer().getId()).isEqualTo(studentPersonOf(debtor).getId()).isNotEqualTo(staff.getId());
            assertThat(event.recipients()).hasSize(1);
        });
    }
}
