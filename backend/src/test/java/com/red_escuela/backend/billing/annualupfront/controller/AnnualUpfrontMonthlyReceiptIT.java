package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.operation.PaymentVerification;
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
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-520")
@Tag("D-521")
@Tag("D-155")
@Tag("D-131")
class AnnualUpfrontMonthlyReceiptIT extends AbstractAnnualUpfrontRequestIT {

    private static final String MONTHLY_ALREADY_USED = "ANNUAL_UPFRONT_MONTHLY_ALREADY_USED";
    private static final LocalDateTime INSIDE_THE_WINDOW = LocalDateTime.of(2027, 3, 2, 10, 0);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private SubmitBankTransferReceiptOperation submitOperation;
    @Autowired
    private RejectPaymentReceiptUseCase rejectReceipt;
    @Autowired
    private ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    private StudentCurrentAccountService accountService;

    private int uploads;

    private JsonNode quote() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/me/program-enrollments/" + learner.programEnrollmentId() + "/annual-upfront/quote")
                .header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String reasonOfQuote() throws Exception {
        return quote().get("reasonCode").asString("");
    }

    private String codeOfRejectedRequest() throws Exception {
        JsonNode body = objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 409).getResponse().getContentAsString());
        return body.get("violations").get(0).get("code").asString("");
    }

    private BankTransferReceiptSubmissionResult submitReceiptOf(Long invoiceId) {
        uploads++;
        BigDecimal total = jdbc.queryForObject("select total from invoice where id = ?", BigDecimal.class, invoiceId);
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(learner.studentId(), List.of(invoiceId), Money.of(total, Currency.ARS),
                LocalDate.of(2027, 3, 2), "OP-MONTHLY-" + uploads + "-" + System.nanoTime(), "0170099220000067797370", "Ana Pérez", "Banco Nación",
                "sha-monthly-" + uploads + "-" + System.nanoTime(), studentId -> "students/" + studentId + "/monthly-" + uploads + ".pdf", "monthly-key-" + System.nanoTime(),
                StatusChangeActor.ofPerson(director.getId()));
        BillingOperationOutcome<BankTransferReceiptSubmissionResult> outcome = pipeline.runOperation(submitOperation, command);
        return ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) outcome).result();
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    private Long issueMarchInstallment() {
        Long invoiceId = issueInstallment(YearMonth.of(2027, 3));
        MutableClock.INSTANCE.setDateTime(INSIDE_THE_WINDOW);
        return invoiceId;
    }

    @Test
    void d520eARealReceiptUploadOnAMonthlyInstallmentSpendsTheOptionAndTheRequestIsRejected() throws Exception {
        Long invoiceId = issueMarchInstallment();
        assertThat(quote().get("reasonCode").asString("")).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");

        submitReceiptOf(invoiceId);

        assertThat(paymentStatusOf(invoiceId)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(reasonOfQuote()).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(codeOfRejectedRequest()).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(count("select count(*) from annual_upfront_request where program_enrollment_id = " + learner.programEnrollmentId())).isZero();
    }

    @Test
    void d520fRejectingTheMonthlyReceiptReturnsTheInvoiceToItsUnpaidStateAndTheReasonFallsBackToTheIssuedInstallment() throws Exception {
        Long invoiceId = issueMarchInstallment();
        BankTransferReceiptSubmissionResult submitted = submitReceiptOf(invoiceId);
        assertThat(reasonOfQuote()).isEqualTo(MONTHLY_ALREADY_USED);

        rejectReceipt.reject(submitted.receiptId(), "No encontramos la transferencia", director);

        assertThat(paymentStatusOf(invoiceId)).isIn("PENDING", "OVERDUE");
        assertThat(reasonOfQuote()).isEqualTo("NO_MONTHS_LEFT_FOR_ANNUAL_UPFRONT");
    }

    @Test
    void d520gAPaidInstallmentOutsideTheLectiveYearPeriodsDoesNotSpendTheOption() throws Exception {
        Long invoiceId = issueInstallment(YearMonth.of(2026, 5));
        MutableClock.INSTANCE.setDateTime(INSIDE_THE_WINDOW);
        jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);

        assertThat(quote().get("eligible").asBoolean()).isTrue();
    }

    @Test
    void d520hAMonthlyInstallmentWithItsKeyReleasedAndNeverPaidDoesNotSpendTheOption() throws Exception {
        Long released = issueMarchInstallment();
        jdbc.update("update invoice_line set charge_key_released_at = now() where invoice_id = ?", released);

        assertThat(quote().get("eligible").asBoolean()).isTrue();
    }

    @Test
    void d520jAPaidInstallmentThatIsLaterFullyCreditedKeepsTheOptionSpent() throws Exception {
        Long invoiceId = issueMarchInstallment();
        BankTransferReceiptSubmissionResult submitted = submitReceiptOf(invoiceId);
        approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none());
        assertThat(paymentStatusOf(invoiceId)).isEqualTo("PAID");
        assertThat(reasonOfQuote()).isEqualTo(MONTHLY_ALREADY_USED);

        jdbc.update("update invoice_line set charge_key_released_at = now() where invoice_id = ?", invoiceId);

        assertThat(paymentStatusOf(invoiceId)).isEqualTo("PAID");
        assertThat(reasonOfQuote()).isEqualTo(MONTHLY_ALREADY_USED);
        assertThat(codeOfRejectedRequest()).isEqualTo(MONTHLY_ALREADY_USED);
    }

    @Test
    void d521dTheRejectedAnnualReceiptLeavesNoLiveChargeKeysAndTheQuoteOffersTheYearAgain() throws Exception {
        JsonNode requested = objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
        Long invoiceId = requested.get("invoiceId").asLong();
        BankTransferReceiptSubmissionResult submitted = submitReceiptOf(invoiceId);
        assertThat(reasonOfQuote()).isEqualTo("ANNUAL_UPFRONT_PAYMENT_ALREADY_REQUESTED");

        rejectReceipt.reject(submitted.receiptId(), "No encontramos la transferencia", director);

        assertThat(count("select count(*) from annual_upfront_request where id = " + requested.get("id").asLong() + " and status = 'DISCARDED' and discard_reason = 'PAYMENT_REJECTED'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and charge_key_released_at is null")).isZero();
        assertThat(quote().get("eligible").asBoolean()).isTrue();
    }

    @Test
    void d520iARequestRacingWithTheUploadOfAMonthlyReceiptSeesTheUploadOnceItHasTheAccountLock() throws Exception {
        Long invoiceId = issueMarchInstallment();
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> uploader = pool.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    accountService.findOrOpenAndLockStudentCurrentAccount(learner.studentId(), Currency.ARS);
                    accountLocked.countDown();
                    awaitUninterruptibly(releaseAccount);
                    jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", invoiceId);
                });
                return null;
            });
            assertThat(accountLocked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<String> requester = pool.submit(this::codeOfRejectedRequest);
            waitUntilSomeoneWaitsForALock();
            releaseAccount.countDown();

            uploader.get(30, TimeUnit.SECONDS);
            assertThat(requester.get(30, TimeUnit.SECONDS)).isEqualTo(MONTHLY_ALREADY_USED);
        } finally {
            releaseAccount.countDown();
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from annual_upfront_request where program_enrollment_id = " + learner.programEnrollmentId())).isZero();
    }

    private void waitUntilSomeoneWaitsForALock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (count("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()") == 0) {
            assertThat(System.nanoTime()).as("the request never waited for the account lock").isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
