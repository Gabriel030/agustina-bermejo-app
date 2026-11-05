package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.BankTransferDetailsRequiredException;
import com.red_escuela.backend.billing.payment.exception.InvoiceAlreadyUnderReviewException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceOfAnotherAccountException;
import com.red_escuela.backend.billing.payment.exception.ReceiptMustCoverOldestInvoicesFirstException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitBankTransferReceiptOperationIT extends AbstractBankTransferReceiptIT {

    Map<String, Object> accountRow(Long accountId) {
        return jdbc.queryForMap("select balance_amount, balance_side, unallocated_credit_amount from student_current_account where id = ?", accountId);
    }

    long entriesOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, accountId);
    }

    long paymentsOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId);
    }

    @Test
    @Tag("D-153")
    @Tag("D-154")
    @Tag("D-139")
    @Tag("D-269")
    void createsPendingPaymentAndProtectsInvoiceWithoutAccountMovements() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Map<String, Object> accountBefore = accountRow(debtor.accountId());
        long entriesBefore = entriesOf(debtor.accountId());

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-8-14"));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.invoiceId()).isEqualTo(march);
            assertThat(item.position()).isEqualTo(1);
            assertThat(item.markedUnderReview()).isTrue();
        });
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        Map<String, Object> payment = jdbc.queryForMap("select status, method_type, gateway, amount, approved_amount, paid_on, method_details ->> 'operationNumber' as op, "
                + "method_details ->> 'transferDate' as transfer_date, method_details ->> 'holderName' as holder from payment where id = ?", result.paymentId());
        assertThat(payment).containsEntry("status", "PENDING").containsEntry("method_type", "BANK_TRANSFER").containsEntry("gateway", "MANUAL")
                .containsEntry("op", "OP-8-14").containsEntry("transfer_date", "2027-03-09").containsEntry("holder", "Ana Pérez");
        assertThat(payment.get("approved_amount")).isNull();
        assertThat(payment.get("paid_on")).isNull();
        Map<String, Object> receipt = jdbc.queryForMap("select invoice_id, payment_id, status, declared_amount, declared_amount_currency, transfer_date, operation_number, "
                + "origin_account, holder_name, uploaded_by_id from bank_transfer_receipt where id = ?", result.receiptId());
        assertThat(receipt).containsEntry("invoice_id", march).containsEntry("payment_id", result.paymentId()).containsEntry("status", "PENDING_REVIEW")
                .containsEntry("declared_amount_currency", "ARS").containsEntry("operation_number", "OP-8-14").containsEntry("uploaded_by_id", staff.getId());
        assertThat(accountRow(debtor.accountId())).isEqualTo(accountBefore);
        assertThat(entriesOf(debtor.accountId())).isEqualTo(entriesBefore);
        assertThat(storedFiles.get()).isEqualTo(1);

        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 15));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 15));

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-154")
    void dueDateIsInclusiveInBuenosAiresTime() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
            Debtor lastMinute = debtor();
            Long lastMinuteInvoice = invoiceOf(lastMinute, "80000.00", MARCH_DUE);
            Debtor firstMinuteAfter = debtor();
            Long afterInvoice = invoiceOf(firstMinuteAfter, "80000.00", MARCH_DUE);

            MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 10, 23, 59));
            BankTransferReceiptSubmissionResult atTheDeadline = submit(command(lastMinute, List.of(lastMinuteInvoice), "80000.00", MARCH_DUE, "OP-LAST"));
            MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 11, 0, 0));
            BankTransferReceiptSubmissionResult afterTheDeadline = submit(command(firstMinuteAfter, List.of(afterInvoice), "80000.00", LocalDate.of(2027, 3, 11), "OP-AFTER"));

            assertThat(atTheDeadline.items().get(0).markedUnderReview()).isTrue();
            assertThat(paymentStatusOfInvoice(lastMinuteInvoice)).isEqualTo("PAYMENT_UNDER_REVIEW");
            assertThat(afterTheDeadline.items().get(0).markedUnderReview()).isFalse();
            assertThat(paymentStatusOfInvoice(afterInvoice)).isEqualTo("PENDING");
            invariants.assertAllBillingInvariantsHold();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    @Tag("D-154")
    @Tag("D-320")
    void uploadedAfterDueDateDoesNotProtect() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 11));

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-LATE"));

        assertThat(result.items().get(0).markedUnderReview()).isFalse();
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, result.paymentId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select marked_under_review from payment_checkout_item where payment_id = ?", Boolean.class, result.paymentId())).isFalse();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-186")
    void overdueInvoiceAcceptsReceiptButStaysOverdue() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 12));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 12));
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", LocalDate.of(2027, 3, 9), "OP-OVERDUE"));

        assertThat(result.items().get(0).markedUnderReview()).isFalse();
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-081")
    void receiptIsLinkedToItsPaymentFromUpload() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-LINK"));

        assertThat(jdbc.queryForObject("select payment_id from bank_transfer_receipt where id = ?", Long.class, result.receiptId())).isEqualTo(result.paymentId());
        assertThat(jdbc.queryForObject("select student_current_account_id from payment where id = ?", Long.class, result.paymentId())).isEqualTo(debtor.accountId());
    }

    @Test
    @Tag("D-243")
    void uploadDateIsKeptAsLateFeeReferenceDate() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 9, 23, 30));

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-DATE"));

        assertThat(result.uploadDate()).isEqualTo(TODAY);
        assertThat(jdbc.queryForObject("select cast(started_at as date) from payment where id = ?", java.sql.Date.class, result.paymentId()).toLocalDate()).isEqualTo(TODAY);
        assertThat(jdbc.queryForObject("select cast(uploaded_at as date) from bank_transfer_receipt where id = ?", java.sql.Date.class, result.receiptId()).toLocalDate())
                .isEqualTo(TODAY);
    }

    @Test
    @Tag("D-153")
    @Tag("D-208")
    void onlyTheInvoicesCoveredByTheDeclaredAmountInOrderAreProtected() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);

        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(april, march), "100000.00", TODAY, "OP-PARTIAL"));

        assertThat(result.items()).extracting(BankTransferReceiptSubmissionResult.Item::invoiceId).containsExactly(march, april);
        assertThat(result.items()).extracting(BankTransferReceiptSubmissionResult.Item::markedUnderReview).containsExactly(true, false);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select invoice_id from bank_transfer_receipt where id = ?", Long.class, result.receiptId())).isEqualTo(march);
        assertThat(jdbc.queryForList("select invoice_id, position, outstanding_at_selection_currency from payment_checkout_item where payment_id = ? order by position",
                result.paymentId())).extracting(row -> row.get("invoice_id")).containsExactly(march, april);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-260")
    void secondReceiptForAnInvoiceUnderReviewIsRejectedAndWritesNothing() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-FIRST"));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        long paymentsBefore = paymentsOf(debtor.accountId());
        int filesBefore = storedFiles.get();

        assertThatThrownBy(() -> submit(command(debtor, List.of(march, april), "160000.00", TODAY, "OP-SECOND"))).isInstanceOf(InvoiceAlreadyUnderReviewException.class);

        assertThat(paymentsOf(debtor.accountId())).isEqualTo(paymentsBefore);
        assertThat(storedFiles.get()).isEqualTo(filesBefore);
        BankTransferReceiptSubmissionResult second = submit(command(debtor, List.of(april), "80000.00", TODAY, "OP-APRIL"));
        assertThat(second.items().get(0).invoiceId()).isEqualTo(april);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-260")
    @Tag("D-153")
    void twoNewReceiptsStartingAtTheSameOverdueInvoiceAreBothAccepted() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 12));
        overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 12));

        BankTransferReceiptSubmissionResult first = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-OVER-1"));
        BankTransferReceiptSubmissionResult second = submit(command(debtor, List.of(march), "80000.00", LocalDate.of(2027, 3, 12), "OP-OVER-2"));

        assertThat(jdbc.queryForList("select invoice_id from bank_transfer_receipt where id in (?, ?)", first.receiptId(), second.receiptId()))
                .extracting(row -> row.get("invoice_id")).containsExactly(march, march);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    void notChoosingTheOldestInvoiceFirstIsRejectedAndWritesNothing() {
        Debtor debtor = debtor();
        invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        assertThatThrownBy(() -> submit(command(debtor, List.of(april), "80000.00", TODAY, "OP-SKIP"))).isInstanceOf(ReceiptMustCoverOldestInvoicesFirstException.class);

        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(storedFiles.get()).isZero();
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-097")
    void invoiceOfAnotherStudentAndUnknownInvoiceAreRejected() {
        Debtor debtor = debtor();
        Debtor other = debtor();
        Long foreign = invoiceOf(other, "80000.00", MARCH_DUE);
        invoiceOf(debtor, "80000.00", MARCH_DUE);

        assertThatThrownBy(() -> submit(command(debtor, List.of(foreign), "80000.00", TODAY, "OP-FOREIGN"))).isInstanceOf(ReceiptInvoiceOfAnotherAccountException.class);
        assertThatThrownBy(() -> submit(command(debtor, List.of(987654321L), "80000.00", TODAY, "OP-MISSING"))).isInstanceOf(InvoiceNotFoundException.class);

        assertThat(paymentStatusOfInvoice(foreign)).isEqualTo("PENDING");
        assertThat(storedFiles.get()).isZero();
    }

    @Test
    @Tag("D-267")
    void aReceiptWithoutHolderIsRejectedBeforeAnythingIsStored() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        SubmitBankTransferReceiptCommand withoutHolder = new SubmitBankTransferReceiptCommand(debtor.studentId(), List.of(march), ars("80000.00"), TODAY, "OP-NOHOLDER",
                "0170099220000067797370", " ", null, "sha", studentId -> "path", nextKey(), actor);

        assertThatThrownBy(() -> submit(withoutHolder)).isInstanceOf(BankTransferDetailsRequiredException.class);

        assertThat(paymentsOf(debtor.accountId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from idempotency_record where idempotency_key = ?", Long.class, withoutHolder.idempotencyKey())).isZero();
    }

    @Test
    @Tag("D-267")
    void maximumColumnLengthsAreStoredWithoutDatabaseErrors() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(debtor.studentId(), List.of(march), ars("9999999999.99"), TODAY, "9".repeat(50),
                "8".repeat(50), "H".repeat(120), "B".repeat(100), "sha", studentId -> "p".repeat(400), nextKey(), actor);

        BankTransferReceiptSubmissionResult result = submit(command);

        assertThat(jdbc.queryForObject("select length(holder_name) from bank_transfer_receipt where id = ?", Integer.class, result.receiptId())).isEqualTo(120);
        assertThat(jdbc.queryForObject("select length(operation_number) from bank_transfer_receipt where id = ?", Integer.class, result.receiptId())).isEqualTo(50);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }
}
