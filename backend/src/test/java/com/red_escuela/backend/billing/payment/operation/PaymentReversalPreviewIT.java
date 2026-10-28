package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentReversalPreviewIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;

    private long approvedTransferFunding(Debtor debtor, List<Long> invoiceIds, String amount) {
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, invoiceIds, amount, TODAY, nextOperationNumber()));
        approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
        return submitted.paymentId();
    }

    private MockHttpServletRequestBuilder chargebackPreview(long paymentId, String token, String amount, String date, String reference) {
        return post("/payments/{id}/chargebacks/preview", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"" + date + "\",\"reference\":"
                        + (reference == null ? "null" : "\"" + reference + "\"") + "}");
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, String token, String amount, String date, String reference) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"" + date + "\",\"reference\":\"" + reference + "\"}");
    }

    private MockHttpServletRequestBuilder reversalPreview(long chargebackId, String token) {
        return post("/chargebacks/{id}/reversal/preview", chargebackId).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder reversal(long chargebackId, String token) {
        return post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder revocationPreview(long paymentId, String token, String reason) {
        return post("/payments/{id}/approval-revocation/preview", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}");
    }

    private MockHttpServletRequestBuilder revocation(long paymentId, String token) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobada por error\"}");
    }

    private JsonNode payload(MockHttpServletRequestBuilder request) throws Exception {
        return bodyOf(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private String blockedCode(JsonNode preview) {
        return preview.get("blockedBy").get("code").asText();
    }

    private JsonNode paymentRow(JsonNode list, long paymentId) {
        for (JsonNode row : list) {
            if (row.get("id").asLong() == paymentId) {
                return row;
            }
        }
        throw new AssertionError("The payment " + paymentId + " is not in the listing");
    }

    private long stateFingerprint(Debtor debtor) {
        return jdbc.queryForObject("select (select count(*) from current_account_entry where student_current_account_id = ?) * 1000000"
                + " + (select count(*) from payment_allocation where student_current_account_id = ?) * 1000"
                + " + (select count(*) from chargeback where student_current_account_id = ?)", Long.class, debtor.accountId(), debtor.accountId(), debtor.accountId());
    }

    @ParameterizedTest
    @CsvSource({"ALUMNO,403", "PRECEPTOR,403", "DOCENTE,403", "ADMINISTRATIVO,200", "DIRECTOR,200"})
    @Tag("D-206")
    void onlyStaffPreviewsAChargebackAndItsReversal(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(chargebackPreview(paymentId, token, "100000.00", "2027-03-09", "BANCO-1")).andExpect(status().is(expectedStatus));
        mockMvc.perform(reversalPreview(987654321L, token)).andExpect(status().is(expectedStatus == 200 ? 404 : expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({"ALUMNO,403", "PRECEPTOR,403", "DOCENTE,403", "ADMINISTRATIVO,403", "DIRECTOR,200"})
    @Tag("D-206")
    void onlyTheDirectorPreviewsAnApprovalRevocation(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(revocationPreview(paymentId, token, "Aprobada por error")).andExpect(status().is(expectedStatus));
    }

    @Test
    @Tag("D-262")
    @Tag("D-206")
    void aChargebackPreviewListsTheFundedInvoicesAndTheCreditWithoutChangingAnything() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransferFunding(debtor, List.of(march), "80000.00");
        long before = stateFingerprint(debtor);

        JsonNode preview = payload(chargebackPreview(paymentId, staffToken("ADMINISTRATIVO"), "80000.00", "2027-03-09", "BANCO-1"));

        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("blockedBy").isNull()).isTrue();
        assertThat(preview.get("paymentStatus").asText()).isEqualTo("APPROVED");
        assertThat(decimalOf(preview.get("creditToReverse"))).isEqualByComparingTo("80000.00");
        assertThat(preview.get("invoiceIdsFundedByPayment")).extracting(JsonNode::asLong).containsExactly(march);
        assertThat(preview.get("effects").get("calculated").asBoolean()).isTrue();
        assertThat(stateFingerprint(debtor)).isEqualTo(before);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("APPROVED");
    }

    @Test
    @Tag("D-262")
    @Tag("D-206")
    void aChargebackPreviewIsRecalculatedWithTheReceivedDateAmountAndReference() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        String token = staffToken("ADMINISTRATIVO");

        assertThat(payload(chargebackPreview(paymentId, token, "100000.00", "2027-03-09", "BANCO-1")).get("allowed").asBoolean()).isTrue();
        assertThat(blockedCode(payload(chargebackPreview(paymentId, token, "100000.00", "2027-03-10", "BANCO-1")))).isEqualTo("CHARGEBACK_DATE_INVALID");
        assertThat(blockedCode(payload(chargebackPreview(paymentId, token, "100000.00", "2027-02-28", "BANCO-1")))).isEqualTo("CHARGEBACK_DATE_INVALID");
        assertThat(payload(chargebackPreview(paymentId, token, "100000.00", "2027-03-01", "BANCO-1")).get("allowed").asBoolean()).isTrue();
        assertThat(blockedCode(payload(chargebackPreview(paymentId, token, "99999.99", "2027-03-09", "BANCO-1")))).isEqualTo("CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT");
        assertThat(blockedCode(payload(chargebackPreview(paymentId, token, "100000.00", "2027-03-09", " ")))).isEqualTo("CHARGEBACK_REFERENCE_INVALID");
        assertThat(blockedCode(payload(chargebackPreview(paymentId, token, "100000.00", "2027-03-09", null)))).isEqualTo("CHARGEBACK_REFERENCE_INVALID");
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    @Tag("D-427")
    void aChargebackAndARevocationPreviewShowThePendingRefundPartsThatBlockThem() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        mockMvc.perform(requestOnBehalf(debtor, administrative, "40000.00")).andExpect(status().isAccepted());
        long before = stateFingerprint(debtor);

        JsonNode chargebackPreview = payload(chargebackPreview(paymentId, administrative, "100000.00", "2027-03-09", "BANCO-1"));
        JsonNode revocationPreview = payload(revocationPreview(paymentId, staffToken("DIRECTOR"), "Aprobada por error"));

        for (JsonNode preview : List.of(chargebackPreview, revocationPreview)) {
            assertThat(preview.get("allowed").asBoolean()).isFalse();
            assertThat(blockedCode(preview)).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
            assertThat(preview.get("pendingRefunds")).hasSize(1);
            assertThat(preview.get("pendingRefunds").get(0).get("status").asText()).isEqualTo("REQUESTED");
            assertThat(decimalOf(preview.get("pendingRefunds").get(0).get("amount"))).isEqualByComparingTo("40000.00");
        }
        assertThat(stateFingerprint(debtor)).isEqualTo(before);
    }

    @Test
    @Tag("D-206")
    void aRevocationPreviewChecksTheReasonTheMethodAndTheState() throws Exception {
        Debtor debtor = debtor();
        long transfer = transferPayment(debtor, "100000.00", EARLY_MARCH);
        long cash = cashPayment(debtor, "50000.00", EARLY_MARCH);
        long online = onlinePayment(debtor, "30000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");

        assertThat(payload(revocationPreview(transfer, director, "Aprobada por error")).get("allowed").asBoolean()).isTrue();
        assertThat(blockedCode(payload(revocationPreview(transfer, director, "  ")))).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
        assertThat(blockedCode(payload(revocationPreview(cash, director, "Aprobada por error")))).isEqualTo("ILLEGAL_PAYMENT_STATUS_TRANSITION");
        assertThat(blockedCode(payload(revocationPreview(online, director, "Aprobada por error")))).isEqualTo("ILLEGAL_PAYMENT_STATUS_TRANSITION");
        mockMvc.perform(revocation(transfer, director)).andExpect(status().isOk());
        assertThat(blockedCode(payload(revocationPreview(transfer, director, "Aprobada por error")))).isEqualTo("ILLEGAL_PAYMENT_STATUS_TRANSITION");
    }

    @Test
    @Tag("D-206")
    void aReversalPreviewFollowsTheChargebackLifecycle() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransferFunding(debtor, List.of(march), "80000.00");
        String administrative = staffToken("ADMINISTRATIVO");
        long chargebackId = bodyOf(mockMvc.perform(chargeback(paymentId, administrative, "80000.00", "2027-03-09", "BANCO-1")).andExpect(status().isCreated()).andReturn())
                .get("chargebackId").asLong();
        long before = stateFingerprint(debtor);

        JsonNode open = payload(reversalPreview(chargebackId, administrative));

        assertThat(open.get("allowed").asBoolean()).isTrue();
        assertThat(open.get("paymentStatus").asText()).isEqualTo("CHARGED_BACK");
        assertThat(decimalOf(open.get("creditToReverse"))).isEqualByComparingTo("80000.00");
        assertThat(stateFingerprint(debtor)).isEqualTo(before);
        mockMvc.perform(reversal(chargebackId, administrative)).andExpect(status().isOk());
        JsonNode reversed = payload(reversalPreview(chargebackId, administrative));
        assertThat(reversed.get("allowed").asBoolean()).isFalse();
        assertThat(blockedCode(reversed)).isEqualTo("CHARGEBACK_ALREADY_REVERSED");
        mockMvc.perform(reversalPreview(987654321L, administrative)).andExpect(status().isNotFound());
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    @Tag("D-427")
    void theStudentPaymentListingReportsTheChargebackThePendingRefundsAndTheAllowedActionsOfEachPayment() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long chargedBack = approvedTransferFunding(debtor, List.of(march), "80000.00");
        long withPendingRefund = transferPayment(debtor, "100000.00", EARLY_MARCH);
        long plainCash = cashPayment(debtor, "50000.00", LocalDate.of(2027, 2, 20));
        String administrative = staffToken("ADMINISTRATIVO");
        long chargebackId = bodyOf(mockMvc.perform(chargeback(chargedBack, administrative, "80000.00", "2027-03-09", "BANCO-1")).andExpect(status().isCreated()).andReturn())
                .get("chargebackId").asLong();
        mockMvc.perform(requestOnBehalf(debtor, administrative, "40000.00")).andExpect(status().isAccepted());

        JsonNode list = bodyOf(mockMvc.perform(get("/students/{id}/payments", debtor.studentId()).header("Authorization", "Bearer " + administrative))
                .andExpect(status().isOk()).andReturn());

        JsonNode chargedBackRow = paymentRow(list, chargedBack);
        assertThat(chargedBackRow.get("status").asText()).isEqualTo("CHARGED_BACK");
        assertThat(chargedBackRow.get("chargeback").get("chargebackId").asLong()).isEqualTo(chargebackId);
        assertThat(chargedBackRow.get("chargeback").get("chargedBackOn").asText()).isEqualTo("2027-03-09");
        assertThat(chargedBackRow.get("chargeback").get("reference").asText()).isEqualTo("BANCO-1");
        assertThat(chargedBackRow.get("chargeback").get("reversedOn").isNull()).isTrue();
        assertThat(chargedBackRow.get("pendingRefunds").get("count").asInt()).isZero();
        assertThat(chargedBackRow.get("actions").get("reverseChargeback").get("allowed").asBoolean()).isTrue();
        assertThat(chargedBackRow.get("actions").get("registerChargeback").get("allowed").asBoolean()).isFalse();
        assertThat(chargedBackRow.get("actions").get("registerChargeback").get("blockedBy").get("code").asText()).isEqualTo("ILLEGAL_PAYMENT_STATUS_TRANSITION");

        JsonNode pendingRow = paymentRow(list, withPendingRefund);
        assertThat(pendingRow.get("chargeback").isNull()).isTrue();
        assertThat(pendingRow.get("pendingRefunds").get("count").asInt()).isEqualTo(1);
        assertThat(decimalOf(pendingRow.get("pendingRefunds").get("amount"))).isEqualByComparingTo("40000.00");
        assertThat(pendingRow.get("pendingRefunds").get("parts").get(0).get("status").asText()).isEqualTo("REQUESTED");
        assertThat(pendingRow.get("actions").get("registerChargeback").get("blockedBy").get("code").asText()).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
        assertThat(pendingRow.get("actions").get("revokeApproval").get("blockedBy").get("code").asText()).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
        assertThat(pendingRow.get("actions").get("reverseChargeback").get("blockedBy").get("code").asText()).isEqualTo("CHARGEBACK_NOT_FOUND");

        JsonNode cashRow = paymentRow(list, plainCash);
        assertThat(cashRow.get("pendingRefunds").get("count").asInt()).isZero();
        assertThat(cashRow.get("actions").get("registerChargeback").get("allowed").asBoolean()).isTrue();
        assertThat(cashRow.get("actions").get("revokeApproval").get("blockedBy").get("code").asText()).isEqualTo("ILLEGAL_PAYMENT_STATUS_TRANSITION");
    }

    @Test
    @Tag("D-206")
    void theOwnPaymentListingOfTheStudentDoesNotExposeTheStaffOnlyFields() throws Exception {
        Debtor debtor = debtor();
        transferPayment(debtor, "100000.00", EARLY_MARCH);

        JsonNode list = bodyOf(mockMvc.perform(get("/me/payments").header("Authorization", "Bearer " + studentTokenOf(debtor))).andExpect(status().isOk()).andReturn());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("actions").isNull()).isTrue();
        assertThat(list.get(0).get("pendingRefunds").isNull()).isTrue();
        assertThat(list.get(0).get("chargeback").isNull()).isTrue();
    }

    @Test
    @Tag("D-206")
    void theStudentPaymentListingRejectsTheRolesWithoutAccess() throws Exception {
        Debtor debtor = debtor();
        MvcResult forbidden = mockMvc.perform(get("/students/{id}/payments", debtor.studentId()).header("Authorization", "Bearer " + staffToken("DOCENTE"))).andReturn();

        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
    }
}
