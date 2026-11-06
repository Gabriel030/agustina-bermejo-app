package com.red_escuela.backend.billing.annualupfront.controller;

import tools.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.invoice.service.StudentSnapshotFinder;
import java.time.YearMonth;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.AnnualUpfrontPaymentAlreadyInitiatedException;
import com.red_escuela.backend.billing.payment.exception.AnnualUpfrontPaymentExpiredException;
import com.red_escuela.backend.billing.payment.exception.AnnualUpfrontReceiptAmountMustBeTotalException;
import com.red_escuela.backend.billing.payment.exception.AnnualUpfrontReceiptLateException;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
@Tag("D-155")
@Tag("D-138")
@Tag("D-521")
class AnnualUpfrontTransferPaymentIT extends AbstractAnnualUpfrontRequestIT {
    private static final LocalDate DAY_ONE = LocalDate.of(2027, 3, 2);
    private static final LocalDate DAY_TWO = LocalDate.of(2027, 3, 3);
    private static final LocalDate DAY_FOUR = LocalDate.of(2027, 3, 5);
    private static final LocalDate DAY_FIVE = LocalDate.of(2027, 3, 6);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private SubmitBankTransferReceiptOperation submitOperation;
    @Autowired
    private ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    private RejectPaymentReceiptUseCase rejectReceipt;
    @Autowired
    private AnnualUpfrontDeadlineExpirer expirer;

    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private InvoiceNumberGenerator numberGenerator;
    @Autowired
    private StudentSnapshotFinder snapshotFinder;
    @Autowired
    private StudentCurrentAccountService currentAccountService;

    private JsonNode requestedYear;
    private int uploads;

    private JsonNode requestTheYear() throws Exception {
        requestedYear = objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
        return requestedYear;
    }

    private String totalOf(JsonNode requested) {
        return requested.get("total").get("amount").asString("");
    }

    private BankTransferReceiptSubmissionResult submit(JsonNode requested, String amount, LocalDate transferDate) {
        uploads++;
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(learner.studentId(), List.of(requested.get("invoiceId").asLong()),
                Money.of(new BigDecimal(amount), Currency.ARS), transferDate, "OP-ANUAL-" + uploads + "-" + System.nanoTime(), "0170099220000067797370", "Ana Pérez", "Banco Nación",
                "sha-annual-" + uploads + "-" + System.nanoTime(), studentId -> "students/" + studentId + "/annual-" + uploads + ".pdf", "annual-key-" + System.nanoTime(),
                StatusChangeActor.ofPerson(director.getId()));
        BillingOperationOutcome<BankTransferReceiptSubmissionResult> outcome = pipeline.runOperation(submitOperation, command);
        return ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) outcome).result();
    }

    private String requestStatus(JsonNode requested) {
        return jdbc.queryForObject("select status from annual_upfront_request where id = ?", String.class, requested.get("id").asLong());
    }

    private String invoiceDocumentStatus(JsonNode requested) {
        return jdbc.queryForObject("select document_status from invoice where id = ?", String.class, requested.get("invoiceId").asLong());
    }

    @Test
    void aReceiptUploadedWithinTheDeadlineMovesTheRequestUnderReviewAndTheDeadlineNoLongerDiscardsIt() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));

        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 5, 12, 0));

        assertThat(requestStatus(requested)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(count("select count(*) from annual_upfront_request where id = " + requested.get("id").asLong() + " and payment_id = " + submitted.paymentId())).isEqualTo(1);
        assertThat(expirer.expireRequestsPastDeadline().expired()).isZero();
        assertThat(invoiceDocumentStatus(requested)).isEqualTo("AWAITING_PAYMENT");
        assertThat(count("select count(*) from payment_checkout_item where payment_id = " + submitted.paymentId())).isEqualTo(1);
    }

    @Test
    void approvingAfterTheDeadlineIssuesTheInvoiceOnTheApprovalDayAllocatesItAndConfirmsTheRequest() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 6, 11, 0));

        approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none());

        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'ISSUED' and payment_status = 'PAID' and issue_date = date '2027-03-06' and invoice_number is not null"))
                .isEqualTo(1);
        assertThat(requestStatus(requested)).isEqualTo("CONFIRMED");
        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId + " and entry_type = 'INVOICE_ISSUED'")).isEqualTo(1);
        assertThat(count("select count(*) from current_account_entry where payment_id = " + submitted.paymentId() + " and entry_type = 'PAYMENT_RECEIVED'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = (select student_current_account_id from invoice where id = ?)",
                BigDecimal.class, invoiceId)).isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void approvingTwiceChangesNothingTheSecondTime() throws Exception {
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none());

        assertThatThrownBy(() -> approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none())).isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class);

        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId + " and entry_type = 'INVOICE_ISSUED'")).isEqualTo(1);
        assertThat(count("select count(*) from current_account_entry where payment_id = " + submitted.paymentId() + " and entry_type = 'PAYMENT_RECEIVED'")).isEqualTo(1);
        assertThat(requestStatus(requested)).isEqualTo("CONFIRMED");
    }

    @Test
    void aReceiptUploadedAfterTheDeadlineIsRejectedAndTheRequestKeepsItsCourseTowardsExpiry() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 5, 9, 0));

        assertThatThrownBy(() -> submit(requested, totalOf(requested), DAY_TWO)).isInstanceOf(AnnualUpfrontReceiptLateException.class);

        assertThat(count("select count(*) from payment where student_current_account_id in (select student_current_account_id from invoice where id = " + requested.get("invoiceId").asLong() + ")"))
                .isZero();
        assertThat(expirer.expireRequestsPastDeadline().expired()).isEqualTo(1);
        assertThat(requestStatus(requested)).isEqualTo("DISCARDED");
    }

    @Test
    void aReceiptForAnAmountDifferentFromTheTotalIsRejected() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));

        assertThatThrownBy(() -> submit(requested, "1000.00", DAY_TWO)).isInstanceOf(AnnualUpfrontReceiptAmountMustBeTotalException.class);

        assertThat(requestStatus(requested)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void payingARequestAlreadyUnderReviewIsAConflict() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        submit(requested, totalOf(requested), DAY_TWO);

        assertThatThrownBy(() -> submit(requested, totalOf(requested), DAY_TWO)).isInstanceOf(AnnualUpfrontPaymentAlreadyInitiatedException.class);
    }

    @Test
    void payingAnExpiredRequestIsAConflictThatInvitesToRequestAgain() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 1));
        expirer.expireRequestsPastDeadline();

        assertThatThrownBy(() -> submit(requested, totalOf(requested), LocalDate.of(2027, 3, 4))).isInstanceOf(AnnualUpfrontPaymentExpiredException.class);
    }

    @Test
    void rejectingTheReceiptDiscardsTheInvoiceWithItsReasonFreesTheChargeKeysAndLetsTheStudentRequestAgain() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 6, 11, 0));

        rejectReceipt.reject(submitted.receiptId(), "No encontramos la transferencia", director);

        assertThat(count("select count(*) from annual_upfront_request where id = " + requested.get("id").asLong() + " and status = 'DISCARDED' and discard_reason = 'PAYMENT_REJECTED' and decided_at is not null"))
                .isEqualTo(1);
        assertThat(invoiceDocumentStatus(requested)).isEqualTo("DISCARDED");
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and charge_key_released_at is null")).isZero();
        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId)).isZero();
        assertThat(requestTheYear().get("status").asString("")).isEqualTo("AWAITING_PAYMENT");
        invariants.assertAllBillingInvariantsHold();
    }

    private Throwable[] runConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = List.of(pool.submit(() -> attempt(start, first)), pool.submit(() -> attempt(start, second)));
            start.countDown();
            return new Throwable[]{futures.get(0).get(), futures.get(1).get()};
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable attempt(CountDownLatch start, Callable<?> work) throws InterruptedException {
        start.await();
        try {
            work.call();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    @Test
    void twoThreadsApprovingTheSameReceiptIssueAndCreditExactlyOnce() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 6, 11, 0));

        Throwable[] outcomes = runConcurrently(() -> approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none()),
                () -> approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none()));

        long failures = java.util.Arrays.stream(outcomes).filter(java.util.Objects::nonNull).count();
        assertThat(failures).as(java.util.Arrays.toString(outcomes)).isEqualTo(1);
        Throwable loser = outcomes[0] != null ? outcomes[0] : outcomes[1];
        assertThat(loser).isInstanceOf(com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException.class);
        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId + " and entry_type = 'INVOICE_ISSUED'")).isEqualTo(1);
        assertThat(count("select count(*) from current_account_entry where payment_id = " + submitted.paymentId() + " and entry_type = 'PAYMENT_RECEIVED'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = (select student_current_account_id from invoice where id = ?)",
                BigDecimal.class, invoiceId)).isEqualByComparingTo("0");
        assertThat(requestStatus(requested)).isEqualTo("CONFIRMED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theExpiryRunningAtTheLastInstantNeverDiscardsARequestWhoseReceiptIsBeingSubmitted() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 0));

        Throwable[] outcomes = runConcurrently(() -> submit(requested, totalOf(requested), LocalDate.of(2027, 3, 4)), () -> expirer.expireRequestsPastDeadline());

        assertThat(outcomes[0]).as(java.util.Arrays.toString(outcomes)).isNull();
        assertThat(outcomes[1]).as(java.util.Arrays.toString(outcomes)).isNull();
        assertThat(requestStatus(requested)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(invoiceDocumentStatus(requested)).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void aReceiptUploadedExactlyOnTheDeadlineIsAcceptedAndOneSecondLaterIsRejected() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 0, 1));

        assertThatThrownBy(() -> submit(requested, totalOf(requested), LocalDate.of(2027, 3, 4))).isInstanceOf(AnnualUpfrontReceiptLateException.class);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 4, 10, 0));
        submit(requested, totalOf(requested), LocalDate.of(2027, 3, 4));

        assertThat(requestStatus(requested)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    void theAnnualInvoiceIsSettledFirstAndDoesNotLetOlderOpenDebtTakeItsCredit() throws Exception {
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        Long accountId = transactionTemplate.execute(status -> currentAccountService.findOrOpenAndLockStudentCurrentAccount(learner.studentId(), Currency.ARS).getId());
        Long olderDebt = transactionTemplate.execute(status -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE, InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Concepto", YearMonth.of(2027, 2), null, null, null, null, null, "AB:" + System.nanoTime(),
                    com.red_escuela.backend.shared.domain.NonNegativeMoney.of(Money.of(new BigDecimal("50000.00"), Currency.ARS)));
            StatusChangeActor actor = StatusChangeActor.ofPerson(director.getId());
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, actor);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 10),
                    snapshotFinder.findSnapshotOfStudent(learner.studentId()), actor);
            Invoice saved = invoiceRepository.save(draft);
            currentAccountService.recordInvoiceIssuedDebit(currentAccountService.lockStudentCurrentAccount(accountId), saved, LocalDate.of(2027, 2, 1));
            return saved.getId();
        });
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        BankTransferReceiptSubmissionResult submitted = submit(requested, totalOf(requested), DAY_TWO);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 12, 0));

        approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none());

        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and payment_status = 'PAID'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + olderDebt + " and payment_status = 'PAID'")).isZero();
    }
}
