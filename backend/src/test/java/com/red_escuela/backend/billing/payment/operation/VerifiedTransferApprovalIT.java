package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.LegacyReceiptHasNoVerifiablePaymentException;
import com.red_escuela.backend.billing.payment.exception.PaymentDateInFutureException;
import com.red_escuela.backend.billing.payment.exception.PaymentReviewNoteTooLongException;
import com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException;
import com.red_escuela.backend.billing.payment.exception.VerifiedPaymentAmountInvalidException;
import com.red_escuela.backend.billing.payment.exception.VerifiedPaymentCurrencyMismatchException;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerifiedTransferApprovalIT extends AbstractPendingTransferBridgeIT {

    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;

    private PaymentVerification amountOf(String amount) {
        return new PaymentVerification(ars(amount), null, null);
    }

    private BankTransferReceipt approveWith(BankTransferReceiptSubmissionResult submitted, PaymentVerification verification) {
        return approveReceipt.approve(submitted.receiptId(), staff, verification);
    }

    private BigDecimal unallocatedCreditOf(Debtor debtor) {
        return jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, debtor.accountId());
    }

    private Map<String, Object> creditEntryOf(Long paymentId) {
        return jdbc.queryForMap("select amount, effective_date, cast(recorded_at as date) as recorded_on from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'",
                paymentId);
    }

    private long creditEntriesOf(Debtor debtor) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class,
                debtor.accountId());
    }

    private String rejectionCodeOf(Long receiptId) {
        return jdbc.queryForObject("select rejection_code from bank_transfer_receipt where id = ?", String.class, receiptId);
    }

    private String receiptRejectionReason(Long receiptId) {
        return jdbc.queryForObject("select rejection_reason from bank_transfer_receipt where id = ?", String.class, receiptId);
    }

    @Test
    @Tag("D-154")
    @Tag("D-169")
    void approvingLaterWithTheVerifiedDateKeepsTheUploadDateAsEffectiveDate() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B02");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 18));

        BankTransferReceipt approved = approveWith(submitted, new PaymentVerification(ars("80000.00"), LocalDate.of(2027, 3, 9), "Verificado en el home banking"));

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        Map<String, Object> payment = paymentRow(submitted.paymentId());
        assertThat(payment.get("paid_on").toString()).isEqualTo("2027-03-09");
        assertThat((BigDecimal) payment.get("approved_amount")).isEqualByComparingTo("80000.00");
        assertThat(jdbc.queryForObject("select review_note from payment where id = ?", String.class, submitted.paymentId())).isEqualTo("Verificado en el home banking");
        Map<String, Object> credit = creditEntryOf(submitted.paymentId());
        assertThat(credit.get("effective_date").toString()).isEqualTo("2027-03-09");
        assertThat(credit.get("recorded_on").toString()).isEqualTo("2027-03-18");
        assertThat((BigDecimal) credit.get("amount")).isEqualByComparingTo("80000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-154")
    void rejectingAfterTheDueDateReturnsTheInvoiceToOverdueAndNotifiesWithTheOriginalUploadDate() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B03");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 18));

        reject(submitted, "No encontramos la transferencia");

        assertThat(rejectionCodeOf(submitted.receiptId())).isEqualTo("REVIEWER_REJECTION");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        assertThat(dueDateOfInvoice(march)).isEqualTo(MARCH_DUE);
        assertThat(rejectedEvents()).singleElement().satisfies(event -> {
            assertThat(event.receipt().getId()).isEqualTo(submitted.receiptId());
            assertThat(event.receipt().getUploadedAt().toLocalDate()).isEqualTo(TODAY);
            assertThat(event.receipt().getRejectionReason()).isEqualTo("No encontramos la transferencia");
        });
        assertThat(creditEntriesOf(debtor)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    @Tag("D-208")
    @Tag("D-235")
    void verifiedForLessThanDeclaredCreditsOnlyWhatWasVerifiedAndTheInvoiceLosesItsProtection() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B14");

        BankTransferReceipt approved = approveWith(submitted, amountOf("30000.00"));

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        Map<String, Object> payment = paymentRow(submitted.paymentId());
        assertThat(payment).containsEntry("status", "APPROVED");
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo("80000.00");
        assertThat((BigDecimal) payment.get("approved_amount")).isEqualByComparingTo("30000.00");
        assertThat((BigDecimal) creditEntryOf(submitted.paymentId()).get("amount")).isEqualByComparingTo("30000.00");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("30000.00");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(dueDateOfInvoice(march)).isEqualTo(MARCH_DUE);
        assertThat(applicationEvents.stream(PaymentReceiptAppliedToOlderDebtEvent.class).toList()).isEmpty();
        assertThat(approvedEvents()).hasSize(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-134")
    void verifiedForMoreThanTheInvoiceSettlesItAndLeavesTheRestAsCredit() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B15");

        approveWith(submitted, amountOf("85000.00"));

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("5000.00");
        assertThat((BigDecimal) creditEntryOf(submitted.paymentId()).get("amount")).isEqualByComparingTo("85000.00");
        assertThat(jdbc.queryForObject("select approved_amount from payment where id = ?", BigDecimal.class, submitted.paymentId())).isEqualByComparingTo("85000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-208")
    void verifiedForLessAfterTheDueDateSettlesMarchAndReturnsAprilToOverdueWithItsOriginalDueDate() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march, april), "160000.00", "OP-B16");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAYMENT_UNDER_REVIEW");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 4, 20));

        approveWith(submitted, amountOf("100000.00"));

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("OVERDUE");
        assertThat(dueDateOfInvoice(april)).isEqualTo(APRIL_DUE);
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("20000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-320")
    void aVerifiedTransferDateLaterThanTheUploadDateRejectsTheReceiptAndCreditsNothing() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B17-LATER");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 12));

        assertThatThrownBy(() -> approveWith(submitted, new PaymentVerification(null, LocalDate.of(2027, 3, 12), null)))
                .isInstanceOf(ReceiptTransferDateMismatchException.class)
                .satisfies(failure -> assertThat(ErrorCodeResponse.of(failure).code()).isEqualTo("RECEIPT_TRANSFER_DATE_MISMATCH"));

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(rejectionCodeOf(submitted.receiptId())).isEqualTo("TRANSFER_DATE_MISMATCH");
        assertThat(receiptRejectionReason(submitted.receiptId())).contains("12/03/2027").contains("09/03/2027");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "REJECTED");
        assertThat(creditEntriesOf(debtor)).isZero();
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        assertThat(dueDateOfInvoice(march)).isEqualTo(MARCH_DUE);
        assertThat(rejectedEvents()).singleElement().satisfies(event -> assertThat(event.receipt().getId()).isEqualTo(submitted.receiptId()));
        assertThat(approvedEvents()).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-320")
    void aVerifiedTransferDateEarlierThanTheUploadDateAlsoRejectsTheReceiptAndFreesTheInvoice() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B17-EARLIER");

        assertThatThrownBy(() -> approveWith(submitted, new PaymentVerification(null, LocalDate.of(2027, 3, 8), null))).isInstanceOf(ReceiptTransferDateMismatchException.class);

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(rejectionCodeOf(submitted.receiptId())).isEqualTo("TRANSFER_DATE_MISMATCH");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(creditEntriesOf(debtor)).isZero();
    }

    @Test
    @Tag("D-320")
    void whenNoDateIsVerifiedTheDeclaredTransferDateIsComparedWithTheUploadDate() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 10));
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(march), "80000.00", LocalDate.of(2027, 3, 9), "OP-B17-DECLARED"));

        assertThatThrownBy(() -> approveReceipt.approve(submitted.receiptId(), staff)).isInstanceOf(ReceiptTransferDateMismatchException.class);

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(creditEntriesOf(debtor)).isZero();
    }

    @Test
    @Tag("D-320")
    void aFutureVerifiedDateIsABadRequestAndTheReceiptStaysPending() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-FUTURE");

        assertThatThrownBy(() -> approveWith(submitted, new PaymentVerification(null, LocalDate.of(2027, 3, 10), null)))
                .isInstanceOf(PaymentDateInFutureException.class)
                .satisfies(failure -> assertThat(ErrorCodeResponse.of(failure).code()).isEqualTo("PAYMENT_DATE_IN_FUTURE"));

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("PENDING_REVIEW");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "PENDING");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(creditEntriesOf(debtor)).isZero();
    }

    @Test
    @Tag("D-139")
    void invalidVerificationDataIsRejectedBeforeAnythingChanges() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-INVALID");

        assertThatThrownBy(() -> approveWith(submitted, amountOf("0.00"))).isInstanceOf(VerifiedPaymentAmountInvalidException.class);
        assertThatThrownBy(() -> approveWith(submitted, amountOf("-5.00"))).isInstanceOf(VerifiedPaymentAmountInvalidException.class);
        assertThatThrownBy(() -> approveWith(submitted, amountOf("10000000000.00"))).isInstanceOf(VerifiedPaymentAmountInvalidException.class);
        assertThatThrownBy(() -> approveWith(submitted, new PaymentVerification(Money.of(new BigDecimal("80000.00"), Currency.USD), null, null)))
                .isInstanceOf(VerifiedPaymentCurrencyMismatchException.class);
        assertThatThrownBy(() -> approveWith(submitted, new PaymentVerification(null, null, "x".repeat(501)))).isInstanceOf(PaymentReviewNoteTooLongException.class);

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("PENDING_REVIEW");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "PENDING");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(creditEntriesOf(debtor)).isZero();
    }

    @Test
    @Tag("D-139")
    void theLargestStorableAmountAndANoteOf500CharactersAreAccepted() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-MAX");

        approveWith(submitted, new PaymentVerification(ars("9999999999.99"), null, "n".repeat(500)));

        assertThat(jdbc.queryForObject("select approved_amount from payment where id = ?", BigDecimal.class, submitted.paymentId())).isEqualByComparingTo("9999999999.99");
        assertThat(jdbc.queryForObject("select length(review_note) from payment where id = ?", Integer.class, submitted.paymentId())).isEqualTo(500);
    }

    @Test
    @Tag("D-235")
    void anOlderInvoiceOutsideTheReceiptThatGetsSettledMarksTheReceiptAsAppliedToOlderDebtWithNotice() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-D235");
        Long february = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 2, 10));
        overdueTransition.markAsOverdueWhenPastDueDate(february, TODAY);

        BankTransferReceipt approved = approveWith(submitted, PaymentVerification.none());

        assertThat(approved.getStatus().name()).isEqualTo("APPLIED_TO_DEBT");
        assertThat(paymentStatusOfInvoice(february)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(applicationEvents.stream(PaymentReceiptAppliedToOlderDebtEvent.class).toList()).singleElement()
                .satisfies(event -> assertThat(event.receiptId()).isEqualTo(submitted.receiptId()));
        assertThat(approvedEvents()).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-139")
    void aLegacyReceiptWithoutPaymentCannotCarryVerificationFields() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceipt uploaded = legacyUpload.upload(march, debtor.studentId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 legado".getBytes()));

        assertThatThrownBy(() -> approveReceipt.approve(uploaded.getId(), staff, amountOf("80000.00")))
                .isInstanceOf(LegacyReceiptHasNoVerifiablePaymentException.class);

        assertThat(receiptStatus(uploaded.getId())).isEqualTo("PENDING_REVIEW");
        assertThat(paymentsOf(debtor.accountId())).isZero();
    }
}
