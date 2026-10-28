package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs HTTP refund preview, eligibility and grouped-listing scenarios and its time includes the one-time Spring context start-up")
class RefundSupportIT extends AbstractRefundIT {

    private static final LocalDate FEBRUARY = LocalDate.of(2027, 2, 1);
    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    private JsonNode getJson(String path, String token) throws Exception {
        return bodyOf(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
    }

    private int getStatus(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private JsonNode previewOnBehalf(Debtor debtor, String token, String amount, String account, String holder) throws Exception {
        MockHttpServletRequestBuilder request = refundRequest("/students/" + debtor.studentId() + "/refund-requests/preview", token, amount, account, holder);
        return bodyOf(mockMvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private long refundRowsOf(Debtor debtor) {
        return jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId());
    }

    private long issueRefundAuthorizationRequests() {
        return jdbc.queryForObject("select count(*) from authorization_request where operation_type = 'ISSUE_REFUND'", Long.class);
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    @Tag("D-267")
    void thePreviewReturnsExactlyTheSplitTheRealRequestThenAppliesAndWritesNothing() throws Exception {
        Debtor debtor = debtor();
        long transfer = transferPayment(debtor, "200000.00", FEBRUARY);
        long cash = cashPayment(debtor, "250000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");
        long authorizationRequestsBefore = issueRefundAuthorizationRequests();

        JsonNode preview = previewOnBehalf(debtor, director, "300000.00", REQUEST_ACCOUNT, REQUEST_HOLDER);

        assertThat(refundRowsOf(debtor)).isZero();
        assertThat(issueRefundAuthorizationRequests()).isEqualTo(authorizationRequestsBefore);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("450000.00");
        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("blockedBy").isNull()).isTrue();
        JsonNode previewParts = preview.get("parts");
        assertThat(previewParts).hasSize(2);
        assertThat(previewParts.get(0).get("paymentId").asLong()).isEqualTo(cash);
        assertThat(decimalOf(previewParts.get(0).get("amount"))).isEqualByComparingTo("250000.00");
        assertThat(previewParts.get(0).get("destinationAccount").asText()).isEqualTo(REQUEST_ACCOUNT);
        assertThat(previewParts.get(1).get("paymentId").asLong()).isEqualTo(transfer);
        assertThat(decimalOf(previewParts.get(1).get("amount"))).isEqualByComparingTo("50000.00");
        assertThat(previewParts.get(1).get("destinationAccount").asText()).isEqualTo(ORIGIN_ACCOUNT);
        assertThat(previewParts.get(1).get("destinationHolder").asText()).isEqualTo(ORIGIN_HOLDER);
        assertThat(previewParts.findValues("destinationComplete")).extracting(JsonNode::asBoolean).containsOnly(true);

        JsonNode real = bodyOf(mockMvc.perform(requestOnBehalf(debtor, director, "300000.00")).andExpect(status().isCreated()).andReturn()).get("parts");

        assertThat(real).hasSize(previewParts.size());
        for (int index = 0; index < real.size(); index++) {
            assertThat(real.get(index).get("paymentId").asLong()).isEqualTo(previewParts.get(index).get("paymentId").asLong());
            assertThat(real.get(index).get("methodType").asText()).isEqualTo(previewParts.get(index).get("methodType").asText());
            assertThat(decimalOf(real.get(index).get("amount"))).isEqualByComparingTo(decimalOf(previewParts.get(index).get("amount")));
            assertThat(real.get(index).get("destinationAccount").asText()).isEqualTo(previewParts.get(index).get("destinationAccount").asText());
        }
    }

    @Test
    @Tag("D-267")
    void aPreviewWithoutDestinationForACashPaymentReportsTheMissingDestinationButStillReturnsTheParts() throws Exception {
        Debtor debtor = debtor();
        long cash = cashPayment(debtor, "100000.00", EARLY_MARCH);

        JsonNode preview = previewOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "40000.00", null, null);

        assertThat(preview.get("allowed").asBoolean()).isFalse();
        assertThat(preview.get("blockedBy").get("code").asText()).isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(preview.get("parts")).hasSize(1);
        assertThat(preview.get("parts").get(0).get("paymentId").asLong()).isEqualTo(cash);
        assertThat(preview.get("parts").get(0).get("destinationComplete").asBoolean()).isFalse();
        assertThat(refundRowsOf(debtor)).isZero();
    }

    @Test
    @Tag("D-267")
    void aPreviewWithoutDestinationForATransferKeepsTheReceiptDestinationButStillAsksForTheRequestOne() throws Exception {
        Debtor debtor = debtor();
        transferPayment(debtor, "100000.00", EARLY_MARCH);

        JsonNode preview = previewOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "40000.00", null, null);

        assertThat(preview.get("allowed").asBoolean()).isFalse();
        assertThat(preview.get("blockedBy").get("code").asText()).isEqualTo("REFUND_DESTINATION_REQUIRED");
        assertThat(preview.get("parts")).hasSize(1);
        assertThat(preview.get("parts").get(0).get("destinationAccount").asText()).isEqualTo(ORIGIN_ACCOUNT);
        assertThat(preview.get("parts").get(0).get("destinationComplete").asBoolean()).isTrue();
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    @Tag("D-264")
    void thePreviewExplainsEveryBlockWithTheCodeTheRealRequestWouldReturn() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        onlinePayment(debtor, "200000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");

        assertThat(previewOnBehalf(debtor, director, "0.00", REQUEST_ACCOUNT, REQUEST_HOLDER).get("blockedBy").get("code").asText()).isEqualTo("REFUND_AMOUNT_INVALID");
        assertThat(previewOnBehalf(debtor, director, "300000.01", REQUEST_ACCOUNT, REQUEST_HOLDER).get("blockedBy").get("code").asText())
                .isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        JsonNode aboveManualPayments = previewOnBehalf(debtor, director, "150000.00", REQUEST_ACCOUNT, REQUEST_HOLDER);
        assertThat(aboveManualPayments.get("blockedBy").get("code").asText()).isEqualTo("REFUND_EXCEEDS_AVAILABLE_AMOUNT");
        assertThat(aboveManualPayments.get("parts")).isEmpty();
        assertThat(previewOnBehalf(debtor, director, "100000.00", REQUEST_ACCOUNT, REQUEST_HOLDER).get("allowed").asBoolean()).isTrue();

        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "5000.00", TODAY.minusDays(1));
        JsonNode overdue = previewOnBehalf(debtor, director, "10000.00", REQUEST_ACCOUNT, REQUEST_HOLDER);
        assertThat(overdue.get("allowed").asBoolean()).isFalse();
        assertThat(overdue.get("blockedBy").get("code").asText()).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
        assertThat(codeOf(mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
    }

    @Test
    @Tag("D-159")
    void creditReservedByAPendingRequestShrinksTheNextPreviewAndTheEligibility() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "40000.00")).andExpect(status().isAccepted());
        String director = staffToken("DIRECTOR");

        JsonNode tooMuch = previewOnBehalf(debtor, director, "70000.00", REQUEST_ACCOUNT, REQUEST_HOLDER);
        JsonNode exactlyWhatIsLeft = previewOnBehalf(debtor, director, "60000.00", REQUEST_ACCOUNT, REQUEST_HOLDER);
        JsonNode eligibility = getJson("/students/" + debtor.studentId() + "/refund-eligibility", director);

        assertThat(tooMuch.get("blockedBy").get("code").asText()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(exactlyWhatIsLeft.get("allowed").asBoolean()).isTrue();
        assertThat(decimalOf(eligibility.get("unallocatedCredit"))).isEqualByComparingTo("100000.00");
        assertThat(decimalOf(eligibility.get("reservedByPendingRefunds"))).isEqualByComparingTo("40000.00");
        assertThat(decimalOf(eligibility.get("availableToRefund"))).isEqualByComparingTo("60000.00");
        JsonNode payment = eligibility.get("payments").get(0);
        assertThat(decimalOf(payment.get("credited"))).isEqualByComparingTo("100000.00");
        assertThat(decimalOf(payment.get("refunded"))).isEqualByComparingTo("0.00");
        assertThat(decimalOf(payment.get("pending"))).isEqualByComparingTo("40000.00");
        assertThat(decimalOf(payment.get("refundableNow"))).isEqualByComparingTo("60000.00");
        assertThat(eligibility.get("blockedBy").isNull()).isTrue();
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    @Tag("D-267")
    void theEligibilityListsTheManualPaymentsNewestFirstWithTheReceiptDestinationAndLeavesOnlinePaymentsOut() throws Exception {
        Debtor debtor = debtor();
        long transfer = transferPayment(debtor, "200000.00", FEBRUARY);
        long cash = cashPayment(debtor, "250000.00", EARLY_MARCH);
        onlinePayment(debtor, "50000.00", EARLY_MARCH);

        JsonNode eligibility = getJson("/students/" + debtor.studentId() + "/refund-eligibility", staffToken("ADMINISTRATIVO"));

        assertThat(eligibility.get("currency").asText()).isEqualTo("ARS");
        assertThat(decimalOf(eligibility.get("unallocatedCredit"))).isEqualByComparingTo("500000.00");
        assertThat(decimalOf(eligibility.get("availableToRefund"))).isEqualByComparingTo("500000.00");
        assertThat(eligibility.get("hasOverdueDebt").asBoolean()).isFalse();
        JsonNode payments = eligibility.get("payments");
        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).get("paymentId").asLong()).isEqualTo(cash);
        assertThat(payments.get(0).get("methodType").asText()).isEqualTo("CASH");
        assertThat(payments.get(0).get("paidOn").asText()).isEqualTo(EARLY_MARCH.toString());
        assertThat(payments.get(0).get("receiptDestinationAccount").isNull()).isTrue();
        assertThat(payments.get(1).get("paymentId").asLong()).isEqualTo(transfer);
        assertThat(payments.get(1).get("methodType").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(decimalOf(payments.get(1).get("refundableNow"))).isEqualByComparingTo("200000.00");
        assertThat(payments.get(1).get("receiptDestinationAccount").asText()).isEqualTo(ORIGIN_ACCOUNT);
        assertThat(payments.get(1).get("receiptDestinationHolder").asText()).isEqualTo(ORIGIN_HOLDER);
    }

    @Test
    @Tag("D-264")
    @Tag("D-159")
    void theEligibilityNamesTheBlockingCauseAndAStudentWithoutAccountHasNothingToRefund() throws Exception {
        Debtor indebted = debtor();
        cashPayment(indebted, "100000.00", EARLY_MARCH);
        issueAdministrativeFeeWithLedgerDebit(indebted.accountId(), "5000.00", TODAY.minusDays(1));
        Debtor onlyOnline = debtor();
        onlinePayment(onlyOnline, "80000.00", EARLY_MARCH);
        Long studentWithoutAccount = newStudentId();
        String staff = staffToken("DIRECTOR");

        JsonNode overdue = getJson("/students/" + indebted.studentId() + "/refund-eligibility", staff);
        JsonNode noManualPayment = getJson("/students/" + onlyOnline.studentId() + "/refund-eligibility", staff);
        JsonNode noAccount = getJson("/students/" + studentWithoutAccount + "/refund-eligibility", staff);

        assertThat(overdue.get("hasOverdueDebt").asBoolean()).isTrue();
        assertThat(overdue.get("blockedBy").get("code").asText()).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
        assertThat(decimalOf(overdue.get("availableToRefund"))).isEqualByComparingTo("100000.00");
        assertThat(noManualPayment.get("blockedBy").get("code").asText()).isEqualTo("REFUND_EXCEEDS_AVAILABLE_AMOUNT");
        assertThat(decimalOf(noManualPayment.get("availableToRefund"))).isEqualByComparingTo("80000.00");
        assertThat(noManualPayment.get("payments")).isEmpty();
        assertThat(noAccount.get("blockedBy").get("code").asText()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(decimalOf(noAccount.get("availableToRefund"))).isEqualByComparingTo("0.00");
        assertThat(noAccount.get("payments")).isEmpty();
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void theStudentPreviewsAndSeesItsEligibilityOnTheMeEndpoints() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String student = studentTokenOf(debtor);

        JsonNode preview = bodyOf(mockMvc.perform(refundRequest("/me/refund-requests/preview", student, "30000.00", REQUEST_ACCOUNT, REQUEST_HOLDER))
                .andExpect(status().isOk()).andReturn());
        JsonNode eligibility = getJson("/me/refund-eligibility", student);

        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(decimalOf(preview.get("parts").get(0).get("amount"))).isEqualByComparingTo("30000.00");
        assertThat(decimalOf(eligibility.get("availableToRefund"))).isEqualByComparingTo("100000.00");
        assertThat(getStatus("/me/refund-eligibility?currency=USD", student)).isEqualTo(200);
        assertThat(decimalOf(getJson("/me/refund-eligibility?currency=USD", student).get("availableToRefund"))).isEqualByComparingTo("0.00");
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void theGroupedListingJoinsThePartsOfEachRequestWithItsTotalsAndNewestRequestFirst() throws Exception {
        Debtor debtor = debtor();
        long transfer = transferPayment(debtor, "200000.00", FEBRUARY);
        long cash = cashPayment(debtor, "250000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");
        long twoPartRequestCashPart = bodyOf(mockMvc.perform(requestOnBehalf(debtor, director, "300000.00")).andExpect(status().isCreated()).andReturn())
                .get("parts").get(0).get("id").asLong();
        mockMvc.perform(completion(twoPartRequestCashPart, director, TODAY.toString(), "REF-CASH-1", "CASH")).andExpect(status().isOk());
        long singlePartRequest = bodyOf(mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andExpect(status().isCreated()).andReturn())
                .get("parts").get(0).get("id").asLong();
        jdbc.update("update refund set requested_on = ? where refund_request_id = (select refund_request_id from refund where id = ?)", EARLY_MARCH.minusDays(10),
                twoPartRequestCashPart);

        JsonNode groups = getJson("/students/" + debtor.studentId() + "/refund-requests", staffToken("ADMINISTRATIVO"));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).get("parts")).hasSize(1);
        assertThat(groups.get(0).get("parts").get(0).get("id").asLong()).isEqualTo(singlePartRequest);
        assertThat(decimalOf(groups.get(0).get("totalAmount"))).isEqualByComparingTo("10000.00");
        assertThat(decimalOf(groups.get(0).get("completedAmount"))).isEqualByComparingTo("0.00");
        assertThat(decimalOf(groups.get(0).get("pendingAmount"))).isEqualByComparingTo("10000.00");
        JsonNode twoParts = groups.get(1);
        assertThat(twoParts.get("parts")).hasSize(2);
        assertThat(twoParts.get("parts").findValues("paymentId")).extracting(JsonNode::asLong).containsExactly(cash, transfer);
        assertThat(twoParts.get("parts").findValues("refundRequestId").stream().map(JsonNode::asLong).distinct()).containsExactly(twoParts.get("refundRequestId").asLong());
        assertThat(twoParts.get("requestedOn").asText()).isEqualTo(EARLY_MARCH.minusDays(10).toString());
        assertThat(decimalOf(twoParts.get("totalAmount"))).isEqualByComparingTo("300000.00");
        assertThat(decimalOf(twoParts.get("completedAmount"))).isEqualByComparingTo("250000.00");
        assertThat(decimalOf(twoParts.get("pendingAmount"))).isEqualByComparingTo("50000.00");
        assertThat(twoParts.get("authorizationRequestId").isNull()).isTrue();
    }

    @Test
    @Tag("D-159")
    void theGroupedListingCarriesTheAuthorizationRequestAndTheStudentOnlySeesItsOwnRequests() throws Exception {
        Debtor own = debtor();
        Debtor other = debtor();
        cashPayment(own, "100000.00", EARLY_MARCH);
        cashPayment(other, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(own, staffToken("ADMINISTRATIVO"), "40000.00")).andExpect(status().isAccepted()).andReturn())
                .get("authorizationRequestId").asLong();
        mockMvc.perform(requestOnBehalf(other, staffToken("DIRECTOR"), "10000.00")).andExpect(status().isCreated());

        JsonNode mine = getJson("/me/refund-requests", studentTokenOf(own));

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("authorizationRequestId").asLong()).isEqualTo(authorizationRequestId);
        assertThat(mine.get(0).get("parts").get(0).get("status").asText()).isEqualTo("REQUESTED");
        assertThat(decimalOf(mine.get(0).get("pendingAmount"))).isEqualByComparingTo("40000.00");
        assertThat(getJson("/me/refund-requests", studentTokenOf(debtor()))).isEmpty();
        assertThat(getJson("/me/refunds", studentTokenOf(own))).hasSize(1);
    }

    @Test
    @Tag("D-216")
    void everyRoleOutsideTheAllowedOnesIsRejectedOnThePreviewEligibilityAndGroupedEndpoints() throws Exception {
        Debtor debtor = debtor();
        String student = studentTokenOf(debtor);
        String base = "/students/" + debtor.studentId();

        for (String path : new String[]{base + "/refund-eligibility", base + "/refund-requests"}) {
            assertThat(getStatus(path, student)).isEqualTo(403);
            assertThat(getStatus(path, staffToken("PRECEPTOR"))).isEqualTo(403);
            assertThat(getStatus(path, staffToken("DOCENTE"))).isEqualTo(403);
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus()).isIn(401, 403);
        }
        for (String path : new String[]{"/me/refund-eligibility", "/me/refund-requests"}) {
            assertThat(getStatus(path, staffToken("ADMINISTRATIVO"))).isEqualTo(403);
            assertThat(getStatus(path, staffToken("DIRECTOR"))).isEqualTo(403);
            assertThat(getStatus(path, staffToken("DOCENTE"))).isEqualTo(403);
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus()).isIn(401, 403);
        }
        String studentPreview = base + "/refund-requests/preview";
        assertThat(mockMvc.perform(refundRequest(studentPreview, student, "1000.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(refundRequest(studentPreview, staffToken("PRECEPTOR"), "1000.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(refundRequest("/me/refund-requests/preview", staffToken("DIRECTOR"), "1000.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post("/me/refund-requests/preview").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }

    @Test
    @Tag("D-216")
    void anUnknownStudentIsNotFoundOnEveryStaffSupportEndpoint() throws Exception {
        String director = staffToken("DIRECTOR");

        assertThat(getStatus("/students/999999999/refund-eligibility", director)).isEqualTo(404);
        assertThat(getStatus("/students/999999999/refund-requests", director)).isEqualTo(404);
        assertThat(mockMvc.perform(refundRequest("/students/999999999/refund-requests/preview", director, "1000.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn()
                .getResponse().getStatus()).isEqualTo(404);
    }
}
