package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-229")
@Tag("D-252")
class AdmissionExpiryWithSubmittedReceiptIT extends AbstractBankTransferReceiptIT {

    @Autowired
    private SystemAdmissionInvoiceCancellation cancellation;

    private Outcome cancelWhenUnpaid(Long invoiceId) {
        return inTransaction(() -> cancellation.cancelWhenUnpaid(invoiceId));
    }

    @Test
    @Tag("C-29")
    void anOverdueInvoiceWithAReceiptOfTheNewFlowPendingReviewIsLeftOutOfTheExpiry() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 12));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 12));
        submit(command(debtor, List.of(march), "80000.00", LocalDate.of(2027, 3, 9), "OP-EXPIRY"));
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");

        assertThat(cancelWhenUnpaid(march)).isEqualTo(Outcome.NOT_CANCELLABLE);

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, march)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-27")
    void anOverdueInvoiceWithoutAnyReceiptIsCancelledByTheExpiry() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 12));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 12));

        assertThat(cancelWhenUnpaid(march)).isEqualTo(Outcome.CANCELLED);

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("CANCELLED");
        assertThat(cancelWhenUnpaid(march)).isEqualTo(Outcome.ALREADY_CANCELLED);
    }
}
