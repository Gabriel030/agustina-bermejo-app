package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.PaymentVerification;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShareSource;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingTransferApprovalBridgeIT extends AbstractPendingTransferBridgeIT {

    @Autowired
    InvoicePaymentShareSource paymentShares;
    @Autowired
    UploadPaymentReceiptUseCase legacyUpload;

    @Test
    @Tag("D-081")
    @Tag("D-154")
    @Tag("D-243")
    @Tag("D-235")
    void approvesThePendingPaymentWithoutCreatingAnother() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-APPROVE-1");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 20));

        BankTransferReceipt approved = approve(submitted);

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1L);
        Map<String, Object> payment = paymentRow(submitted.paymentId());
        assertThat(payment).containsEntry("status", "APPROVED").containsEntry("operation_number", "OP-APPROVE-1");
        assertThat((java.math.BigDecimal) payment.get("approved_amount")).isEqualByComparingTo("80000.00");
        assertThat(payment.get("paid_on").toString()).isEqualTo("2027-03-09");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_id from bank_transfer_receipt where id = ?", Long.class, submitted.receiptId())).isEqualTo(submitted.paymentId());
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, debtor.accountId()))
                .isEqualByComparingTo("0");
        assertThat(approvedEvents()).singleElement().satisfies(event -> assertThat(event.payer().getId()).isEqualTo(studentPersonOf(debtor).getId()));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-208")
    @Tag("D-235")
    @Tag("D-153")
    void paysUnderReviewInvoiceAndReleasesUncoveredItems() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        Long may = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 5, 10));
        BankTransferReceiptSubmissionResult first = submitOn(debtor, List.of(march), "80000.00", "OP-FIRST");
        BankTransferReceiptSubmissionResult second = submitOn(debtor, List.of(april, may), "160000.00", "OP-SECOND");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(paymentStatusOfInvoice(may)).isEqualTo("PAYMENT_UNDER_REVIEW");

        approve(second);

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(may)).isEqualTo("PENDING");
        assertThat(dueDateOfInvoice(may)).isEqualTo(LocalDate.of(2027, 5, 10));
        assertThat(paymentRow(first.paymentId())).containsEntry("status", "PENDING");
        assertThat(paymentRow(second.paymentId())).containsEntry("status", "APPROVED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(2L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-208")
    void anUncoveredProtectedInvoiceAfterDueDateReturnsToOverdueWithItsOriginalDueDate() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        Long may = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 5, 10));
        submitOn(debtor, List.of(march), "80000.00", "OP-FIRST-LATE");
        BankTransferReceiptSubmissionResult second = submitOn(debtor, List.of(april, may), "160000.00", "OP-SECOND-LATE");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 20));

        approve(second);

        assertThat(paymentStatusOfInvoice(may)).isEqualTo("OVERDUE");
        assertThat(dueDateOfInvoice(may)).isEqualTo(LocalDate.of(2027, 5, 10));
    }

    @Test
    @Tag("D-376")
    void allocationSourceShowsTheTransferOperationNumber() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-D376");

        approve(submitted);

        List<InvoicePaymentShare> shares = paymentShares.findCurrentSharesByInvoiceId(List.of(march)).resolvedByInvoiceId().get(march);
        assertThat(shares).singleElement().satisfies(share -> {
            assertThat(share.paymentId()).isEqualTo(submitted.paymentId());
            assertThat(share.method()).isEqualTo("TRANSFER");
            assertThat(share.externalReference()).isEqualTo("OP-D376");
            assertThat(share.amountApplied().amount()).isEqualByComparingTo("80000.00");
        });
    }

    @Test
    @Tag("D-270")
    void theAccountHolderIsNotifiedEvenWhenStaffUploadedTheReceipt() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-NOTIFY");
        assertThat(jdbc.queryForObject("select uploaded_by_id from bank_transfer_receipt where id = ?", Long.class, submitted.receiptId())).isEqualTo(staff.getId());

        approve(submitted);

        assertThat(approvedEvents()).singleElement().satisfies(event -> {
            assertThat(event.payer().getId()).isEqualTo(studentPersonOf(debtor).getId()).isNotEqualTo(staff.getId());
            assertThat(event.recipients()).hasSize(1);
        });
    }

    private Long overdueInstallmentAndProgramAdmission(Debtor debtor, List<Long> out) {
        Long february = invoiceOf(debtor, "50000.00", LocalDate.of(2027, 2, 10));
        overdueTransition.markAsOverdueWhenPastDueDate(february, TODAY);
        Long admission = invoiceOf(debtor, "80000.00", MARCH_DUE);
        jdbc.update("update invoice set issuing_reason = 'PROGRAM_ADMISSION' where id = ?", admission);
        out.add(february);
        return admission;
    }

    @Test
    @Tag("D-329")
    void aReceiptOnlyForTheProgramAdmissionPaysTheAdmissionAndLeavesTheOverdueInstallment() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        List<Long> installments = new java.util.ArrayList<>();
        Long admission = overdueInstallmentAndProgramAdmission(debtor, installments);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(admission), "80000.00", "OP-ADMISSION-ONLY");

        approve(submitted);

        assertThat(paymentStatusOfInvoice(admission)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(installments.get(0))).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-329")
    void aReceiptForTheOverdueInstallmentAndTheAdmissionDeclaringTheAdmissionAmountSettlesTheAdmission() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        List<Long> installments = new java.util.ArrayList<>();
        Long admission = overdueInstallmentAndProgramAdmission(debtor, installments);
        Long february = installments.get(0);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(february, admission), "80000.00", "OP-ADMISSION-AND-FEBRUARY");

        assertThat(submitted.items()).extracting(BankTransferReceiptSubmissionResult.Item::invoiceId).containsExactly(admission, february);
        assertThat(submitted.items()).extracting(BankTransferReceiptSubmissionResult.Item::markedUnderReview).containsExactly(true, false);

        approve(submitted);

        assertThat(paymentStatusOfInvoice(admission)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(february)).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-329")
    void aReceiptForAnAdmissionThatIsNotOfAProgramStillRespectsTheOldestFirstOrder() {
        Debtor debtor = debtor();
        List<Long> installments = new java.util.ArrayList<>();
        Long courseInvoice = overdueInstallmentAndProgramAdmission(debtor, installments);
        jdbc.update("update invoice set issuing_reason = 'AD_HOC_CHARGE' where id = ?", courseInvoice);

        assertThatThrownBy(() -> submitOn(debtor, List.of(courseInvoice), "80000.00", "OP-NOT-ADMISSION"))
                .isInstanceOf(com.red_escuela.backend.billing.payment.exception.ReceiptMustCoverOldestInvoicesFirstException.class);
    }

    @Test
    @Tag("D-139")
    void approvingAPaymentThatAlreadyHasItsCreditFailsWithAClearConflictAndCreditsNothingElse() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-DOUBLE-APPROVAL");
        inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(debtor.accountId()), submitted.paymentId(),
                com.red_escuela.backend.shared.domain.NonNegativeMoney.of(ars("80000.00")), TODAY, "credito previo"));

        assertThatThrownBy(() -> approve(submitted))
                .isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentAlreadyApprovedException.class)
                .satisfies(failure -> assertThat(com.red_escuela.backend.shared.exception.ErrorCodeResponse.of((RuntimeException) failure).code())
                        .isEqualTo("PAYMENT_ALREADY_APPROVED"));
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where payment_id = ?", Long.class, submitted.paymentId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-081")
    void legacyReceiptWithoutPaymentStillApprovesThroughOldBridge() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceipt uploaded = legacyUpload.upload(march, debtor.studentId(),
                new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "%PDF-1.4 legado".getBytes()));
        assertThat(jdbc.queryForObject("select payment_id from bank_transfer_receipt where id = ?", Long.class, uploaded.getId())).isNull();
        assertThat(paymentsOf(debtor.accountId())).isZero();

        BankTransferReceipt approved = approveReceipt.approve(uploaded.getId(), staff, PaymentVerification.none());

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(paymentsOf(debtor.accountId())).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        Long linkedPayment = jdbc.queryForObject("select payment_id from bank_transfer_receipt where id = ?", Long.class, uploaded.getId());
        assertThat(paymentRow(linkedPayment)).containsEntry("status", "APPROVED");
    }
}
