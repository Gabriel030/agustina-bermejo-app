package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferUploadDateLateFeeIT extends AbstractLateFeeCorrectionIT {

    private record OverdueMarchWithReceipt(Invoice origin, BankTransferReceiptSubmissionResult submitted, Long debitNoteId) {
    }

    private OverdueMarchWithReceipt marchUploadedOn(int year, int graceDays, int uploadDay, String operationNumber) {
        arrange(year);
        publish(graceDays, "0.002", 10, true, true, d(year, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        BankTransferReceiptSubmissionResult submitted = submitReceiptOn(d(year, 3, uploadDay), List.of(origin.getId()), "100000.00", d(year, 3, uploadDay), operationNumber);
        MutableClock.INSTANCE.setDate(d(year, 4, 1));
        reconcile(origin, d(year, 4, 1));
        awaitEventsDrained();
        return new OverdueMarchWithReceipt(origin, submitted, chargesOf(origin).isEmpty() ? null : debitNoteIdOf(origin, 0));
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private BigDecimal creditedTotalOf(Long invoiceId) {
        return jdbc.queryForObject("select coalesce(sum(total_amount), 0) from credit_note where invoice_id = ?", BigDecimal.class, invoiceId);
    }

    private Map<String, Object> creditEntryOf(Long paymentId) {
        return jdbc.queryForMap("select amount, effective_date from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'", paymentId);
    }

    @Test
    @Tag("D-243")
    @Tag("D-320")
    @Tag("D-378")
    @Tag("D-308")
    void lateUploadedReceiptApprovedAfterACloseAnnulsTheDaysAfterTheUpload() {
        OverdueMarchWithReceipt scenario = marchUploadedOn(2541, 0, 25, "OP-C2-01");
        Long debitNoteId = scenario.debitNoteId();
        assertThat(summaryOf(scenario.origin())).containsExactly("2541-03-12..2541-03-31:20:4000.00");
        assertThat(creditNotesOf(debitNoteId)).isEmpty();

        approveReceiptOn(d(2541, 4, 2), scenario.submitted(), PaymentVerification.none());
        awaitEventsDrained();

        assertThat(creditEntryOf(scenario.submitted().paymentId()).get("effective_date").toString()).isEqualTo("2541-03-25");
        assertThat(paymentStatusOf(scenario.origin().getId())).isEqualTo("PAID");
        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("1200.00");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("14");
        assertScenarioInvariants(d(2541, 4, 2));
    }

    @Test
    @Tag("D-290")
    void rejectedReceiptKeepsEveryLateFee() {
        OverdueMarchWithReceipt scenario = marchUploadedOn(2542, 0, 25, "OP-C2-02");
        Long debitNoteId = scenario.debitNoteId();
        assertThat(summaryOf(scenario.origin())).containsExactly("2542-03-12..2542-03-31:20:4000.00");

        rejectReceiptOn(d(2542, 4, 2), scenario.submitted());
        awaitEventsDrained();
        MutableClock.INSTANCE.setDate(d(2542, 4, 3));
        reconcile(scenario.origin(), d(2542, 4, 3));
        awaitEventsDrained();

        assertThat(paymentStatusOf(scenario.origin().getId())).isEqualTo("OVERDUE");
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("0");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("20");
        assertThat(summaryOf(scenario.origin())).containsExactly("2542-03-12..2542-03-31:20:4000.00");
        assertScenarioInvariants(d(2542, 4, 3));
    }

    @Test
    @Tag("D-320")
    void receiptWithDifferentTransferDateIsRejectedAndAnnulsNothing() {
        OverdueMarchWithReceipt scenario = marchUploadedOn(2543, 0, 25, "OP-C2-03");
        Long debitNoteId = scenario.debitNoteId();

        assertThatThrownBy(() -> approveReceiptOn(d(2543, 4, 2), scenario.submitted(), new PaymentVerification(null, d(2543, 3, 27), null)))
                .isInstanceOf(ReceiptTransferDateMismatchException.class);
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select status from bank_transfer_receipt where id = ?", String.class, scenario.submitted().receiptId())).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class,
                scenario.submitted().paymentId())).isZero();
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("20");
        assertScenarioInvariants(d(2543, 4, 2));
    }

    @Test
    @Tag("D-331")
    @Tag("D-243")
    @Tag("D-153")
    void receiptUploadedWithinGraceAndApprovedAfterACloseChargesNothing() {
        OverdueMarchWithReceipt scenario = marchUploadedOn(2544, 10, 18, "OP-C2-04");
        Long debitNoteId = scenario.debitNoteId();
        assertThat(debitNoteId).isNotNull();
        assertThat(summaryOf(scenario.origin())).containsExactly("2544-03-12..2544-03-31:20:4000.00");

        approveReceiptOn(d(2544, 4, 2), scenario.submitted(), PaymentVerification.none());
        awaitEventsDrained();

        assertThat(creditEntryOf(scenario.submitted().paymentId()).get("effective_date").toString()).isEqualTo("2544-03-18");
        assertThat(paymentStatusOf(scenario.origin().getId())).isEqualTo("PAID");
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("4000.00");
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("CANCELLED");
        assertThat(netChargedDaysOf(debitNoteId)).isZero();
        assertScenarioInvariants(d(2544, 4, 2));
    }
}
