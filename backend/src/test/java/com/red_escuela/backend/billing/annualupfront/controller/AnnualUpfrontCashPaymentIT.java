package com.red_escuela.backend.billing.annualupfront.controller;

import tools.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptCommand;
import com.red_escuela.backend.billing.payment.operation.SubmitBankTransferReceiptOperation;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-442")
@Tag("D-155")
@Tag("D-206")
class AnnualUpfrontCashPaymentIT extends AbstractAnnualUpfrontRequestIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private SubmitBankTransferReceiptOperation submitOperation;
    @Autowired
    private ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    private AnnualUpfrontDeadlineExpirer expirer;

    private JsonNode requestTheYear() throws Exception {
        return objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
    }

    private String directorToken() {
        return jwtService.generateAccessToken(director);
    }

    private ResultActions payInCash(JsonNode requested, String amount, String token, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/students/" + learner.studentId() + "/annual-upfront-requests/" + requested.get("id").asLong() + "/cash-payment")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", idempotencyKey).contentType("application/json")
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"receiptNumber\":\"R-1\"}"));
    }

    private String requestStatus(JsonNode requested) {
        return jdbc.queryForObject("select status from annual_upfront_request where id = ?", String.class, requested.get("id").asLong());
    }

    private String totalOf(JsonNode requested) {
        return requested.get("total").get("amount").asString("");
    }

    private long cashPaymentsOf(Long invoiceId) {
        return count("select count(*) from payment where gateway = 'MANUAL' and student_current_account_id = (select student_current_account_id from invoice where id = " + invoiceId + ")");
    }

    @Test
    void cashForTheTotalWithinTheDeadlineIssuesPaysAndConfirmsInTheSameOperation() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 11, 0));

        payInCash(requested, totalOf(requested), directorToken(), "cash-" + System.nanoTime()).andExpect(status().isCreated());

        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'ISSUED' and payment_status = 'PAID' and issue_date = date '2027-03-03' and invoice_number is not null"))
                .isEqualTo(1);
        assertThat(requestStatus(requested)).isEqualTo("CONFIRMED");
        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId + " and entry_type = 'INVOICE_ISSUED'")).isEqualTo(1);
        assertThat(cashPaymentsOf(invoiceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = (select student_current_account_id from invoice where id = ?)",
                BigDecimal.class, invoiceId)).isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void repeatingTheSameIdempotencyKeyReplaysAndAnotherKeyAfterTheChargeIsAConflict() throws Exception {
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        String key = "cash-" + System.nanoTime();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 11, 0));

        payInCash(requested, totalOf(requested), directorToken(), key).andExpect(status().isCreated());
        payInCash(requested, totalOf(requested), directorToken(), key).andExpect(status().isCreated());
        payInCash(requested, totalOf(requested), directorToken(), key + "-other").andExpect(status().isConflict());

        assertThat(count("select count(*) from current_account_entry where invoice_id = " + invoiceId + " and entry_type = 'INVOICE_ISSUED'")).isEqualTo(1);
        assertThat(cashPaymentsOf(invoiceId)).isEqualTo(1);
    }

    @Test
    void cashForAnAmountDifferentFromTheTotalOrAfterTheDeadlineOrFromAStudentIsRejected() throws Exception {
        JsonNode requested = requestTheYear();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 11, 0));

        payInCash(requested, "1000.00", directorToken(), "cash-a-" + System.nanoTime()).andExpect(status().is4xxClientError());
        payInCash(requested, totalOf(requested), studentToken(), "cash-b-" + System.nanoTime()).andExpect(status().isForbidden());
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 5, 11, 0));
        payInCash(requested, totalOf(requested), directorToken(), "cash-c-" + System.nanoTime()).andExpect(status().is4xxClientError());

        assertThat(requestStatus(requested)).isEqualTo("AWAITING_PAYMENT");
        assertThat(cashPaymentsOf(requested.get("invoiceId").asLong())).isZero();
        assertThat(expirer.expireRequestsPastDeadline().expired()).isEqualTo(1);
    }

    @Test
    void revokingTheApprovedTransferOfTheAnnualReopensTheIssuedInvoiceWithoutDiscardingIt() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        JsonNode requested = requestTheYear();
        Long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 3, 10, 0));
        SubmitBankTransferReceiptCommand command = new SubmitBankTransferReceiptCommand(learner.studentId(), List.of(invoiceId), Money.of(new BigDecimal(totalOf(requested)), Currency.ARS),
                LocalDate.of(2027, 3, 3), "OP-REV-" + System.nanoTime(), "0170099220000067797370", "Ana Pérez", "Banco Nación", "sha-rev-" + System.nanoTime(),
                studentId -> "students/" + studentId + "/rev.pdf", "rev-key-" + System.nanoTime(), StatusChangeActor.ofPerson(director.getId()));
        BankTransferReceiptSubmissionResult submitted = ((BillingOperationOutcome.Processed<BankTransferReceiptSubmissionResult>) pipeline.runOperation(submitOperation, command)).result();
        approveReceipt.approve(submitted.receiptId(), director, PaymentVerification.none());

        mockMvc.perform(post("/payments/" + submitted.paymentId() + "/approval-revocation").header("Authorization", "Bearer " + directorToken())
                .contentType("application/json").content("{\"reason\":\"Aprobada por error\"}")).andExpect(status().is2xxSuccessful());

        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'ISSUED' and payment_status in ('PENDING','OVERDUE')")).isEqualTo(1);
        assertThat(requestStatus(requested)).isEqualTo("CONFIRMED");
        invariants.assertAllBillingInvariantsHold();
    }
}
