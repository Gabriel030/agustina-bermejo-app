package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs 24 HTTP refund-request scenarios, including one with two real threads, and its time includes the one-time Spring context start-up")
class RefundRequestIT extends AbstractRefundIT {

    @Autowired
    AuthorizationRequestExpirer expirer;

    private static final LocalDate FEBRUARY = LocalDate.of(2027, 2, 1);
    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    @Test
    @Tag("D-209")
    @Tag("D-216")
    @Tag("D-159")
    @Tag("D-267")
    void theDirectorRequestSplitsTheAmountFromTheNewestPaymentToTheOldestAndApprovesEveryPartAtOnce() throws Exception {
        Debtor debtor = debtor();
        long transfer = transferPayment(debtor, "200000.00", FEBRUARY);
        long cash = cashPayment(debtor, "250000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "300000.00")).andExpect(status().isCreated()).andReturn();

        JsonNode response = bodyOf(result);
        assertThat(response.get("authorizationRequestId").isNull()).isTrue();
        JsonNode parts = response.get("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).get("paymentId").asLong()).isEqualTo(cash);
        assertThat(parts.get(0).get("methodType").asText()).isEqualTo("CASH");
        assertThat(decimalOf(parts.get(0).get("amount"))).isEqualByComparingTo("250000.00");
        assertThat(parts.get(0).get("destinationAccount").asText()).isEqualTo(REQUEST_ACCOUNT);
        assertThat(parts.get(1).get("paymentId").asLong()).isEqualTo(transfer);
        assertThat(parts.get(1).get("methodType").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(decimalOf(parts.get(1).get("amount"))).isEqualByComparingTo("50000.00");
        assertThat(parts.get(1).get("destinationAccount").asText()).isEqualTo(ORIGIN_ACCOUNT);
        assertThat(parts.get(1).get("destinationHolder").asText()).isEqualTo(ORIGIN_HOLDER);
        assertThat(parts.findValues("status")).extracting(JsonNode::asText).containsOnly("APPROVED");
        assertThat(parts.findValues("refundRequestId").stream().map(JsonNode::asLong).distinct()).hasSize(1);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("450000.00");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-216")
    @Tag("D-203")
    void theAdministrativeRequestWaitsInApprovalsWithEveryPartRequestedAndAnAuthorizationRequestOfTypeIssueRefund() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "40000.00")).andExpect(status().isAccepted()).andReturn();

        JsonNode response = bodyOf(result);
        long authorizationRequestId = response.get("authorizationRequestId").asLong();
        assertThat(authorizationType(authorizationRequestId)).isEqualTo(BillingOperationType.ISSUE_REFUND);
        assertThat(authorizationStatus(authorizationRequestId)).isEqualTo("PENDING");
        assertThat(response.get("parts")).hasSize(1);
        assertThat(response.get("parts").get(0).get("status").asText()).isEqualTo("REQUESTED");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-216")
    @Tag("D-159")
    void theStudentRequestAlwaysWaitsForTheDirector() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(refundRequest("/me/refund-requests", studentTokenOf(debtor), "100000.00", REQUEST_ACCOUNT, REQUEST_HOLDER))
                .andExpect(status().isAccepted()).andReturn();

        long refundId = bodyOf(result).get("parts").get(0).get("id").asLong();
        assertThat(refundStatus(refundId)).isEqualTo("REQUESTED");
        assertThat(refundRow(refundId).get("requested_by_person_id")).isEqualTo(
                jdbc.queryForObject("select person_id from student where id = ?", Long.class, debtor.studentId()));
    }

    @Test
    @Tag("D-216")
    @Tag("D-159")
    void theDirectorApprovalResumesTheRequestAndApprovesItsParts() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "70000.00")).andReturn()).get("authorizationRequestId").asLong();
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);

        mockMvc.perform(decision("approve", authorizationRequestId, staffToken("DIRECTOR"))).andExpect(status().isOk());

        assertThat(authorizationStatus(authorizationRequestId)).isEqualTo("APPROVED");
        assertThat(refundStatus(refundId)).isEqualTo("APPROVED");
        assertThat(refundRow(refundId).get("approved_on")).isNotNull();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void theDirectorRejectionRejectsThePartsWithItsReasonAndMovesNoMoney() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "70000.00")).andReturn()).get("authorizationRequestId").asLong();
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);

        mockMvc.perform(decision("reject", authorizationRequestId, staffToken("DIRECTOR"))).andExpect(status().isOk());

        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(refundRow(refundId).get("rejection_reason")).isEqualTo("decisión de prueba");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    void anExpiredRequestRejectsItsPartsWithTheAutomaticReasonAndTheProcessAsActor() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "70000.00")).andReturn()).get("authorizationRequestId").asLong();
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);
        jdbc.update("update authorization_request set expires_on = ? where id = ?", TODAY.minusDays(1), authorizationRequestId);

        inTransaction(expirer::expireDueRequests);

        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(refundRow(refundId).get("rejection_reason").toString()).contains("venció");
        assertThat(jdbc.queryForObject("select actor_process from refund_status_change where refund_id = ? and event = 'REFUND_REJECTED'", String.class, refundId))
                .isEqualTo("REFUND_REQUEST_EXPIRATION");
    }

    @Test
    @Tag("D-159")
    @Tag("D-264")
    void ifTheStudentOwesOverdueDebtWhenTheDirectorApprovesTheRequestIsRejectedOnResumeAndItsPartsAreRejected() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "70000.00")).andReturn()).get("authorizationRequestId").asLong();
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "5000.00", TODAY.minusDays(1));

        MvcResult result = mockMvc.perform(decision("approve", authorizationRequestId, staffToken("DIRECTOR"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select resume_outcome from authorization_request where id = ?", String.class, authorizationRequestId)).isEqualTo("REJECTED_ON_RESUME");
    }

    @Test
    @Tag("D-264")
    void aDocumentDueTodayIsNotOverdueDebtButOneDueYesterdayBlocksTheRefund() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "5000.00", TODAY);
        String director = staffToken("DIRECTOR");

        mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andExpect(status().isCreated());
        issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "5000.00", TODAY.minusDays(1));
        MvcResult blocked = mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andReturn();

        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(blocked)).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
        assertThat(jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-159")
    void anAmountAboveTheUnallocatedCreditIsRejectedWithNothingWritten() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "100000.01")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isZero();
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    void creditThatCameFromAnOnlinePaymentCannotBeSplitIntoARefundYetAndStaysInFavor() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        onlinePayment(debtor, "200000.00", EARLY_MARCH);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "150000.00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("REFUND_EXCEEDS_AVAILABLE_AMOUNT");
        assertThat(mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "100000.00")).andReturn().getResponse().getStatus()).isEqualTo(201);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("300000.00");
    }

    @Test
    @Tag("D-267")
    void theRequestAlwaysNeedsACompleteDestinationEvenWhenTheTransferReceiptHasOne() throws Exception {
        Debtor debtor = debtor();
        transferPayment(debtor, "100000.00", FEBRUARY);
        String director = staffToken("DIRECTOR");
        String path = "/students/" + debtor.studentId() + "/refund-requests";

        for (String[] destination : new String[][]{{null, null}, {REQUEST_ACCOUNT, null}, {null, REQUEST_HOLDER}, {" ", REQUEST_HOLDER}}) {
            MvcResult result = mockMvc.perform(refundRequest(path, director, "10000.00", destination[0], destination[1])).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(codeOf(result)).isEqualTo("REFUND_DESTINATION_REQUIRED");
        }
        assertThat(jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isZero();
    }

    @Test
    @Tag("D-267")
    void theDestinationOfTheRequestIsValidatedAgainstTheColumnsAtTheirLimits() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");
        String path = "/students/" + debtor.studentId() + "/refund-requests";

        MvcResult tooLongAccount = mockMvc.perform(refundRequest(path, director, "10.00", "a".repeat(51), "Titular")).andReturn();
        MvcResult tooLongHolder = mockMvc.perform(refundRequest(path, director, "10.00", "alias", "t".repeat(121))).andReturn();
        MvcResult atTheLimits = mockMvc.perform(refundRequest(path, director, "10.00", "a".repeat(50), "t".repeat(120))).andReturn();

        assertThat(tooLongAccount.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(tooLongAccount)).isEqualTo("REFUND_DESTINATION_INVALID");
        assertThat(codeOf(tooLongHolder)).isEqualTo("REFUND_DESTINATION_INVALID");
        assertThat(atTheLimits.getResponse().getStatus()).isEqualTo(201);
        long refundId = bodyOf(atTheLimits).get("parts").get(0).get("id").asLong();
        assertThat(refundRow(refundId).get("destination_account")).isEqualTo("a".repeat(50));
        assertThat(refundRow(refundId).get("destination_holder")).isEqualTo("t".repeat(120));
    }

    @Test
    @Tag("D-159")
    void anInvalidAmountIsAClientErrorAndNeverAServerError() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);

        for (String amount : List.of("0", "-5", "10000000000.00")) {
            MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), amount)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(codeOf(result)).isEqualTo("REFUND_AMOUNT_INVALID");
        }
    }

    @Test
    @Tag("D-267")
    void aTransferPartAlwaysGoesToTheAccountOfTheReceiptAndTheOneInTheRequestIsIgnoredForIt() throws Exception {
        Debtor debtor = debtor();
        transferPayment(debtor, "100000.00", FEBRUARY);

        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "30000.00")).andExpect(status().isCreated()).andReturn();

        long refundId = bodyOf(result).get("parts").get(0).get("id").asLong();
        assertThat(refundRow(refundId).get("destination_account")).isEqualTo(ORIGIN_ACCOUNT);
        assertThat(refundRow(refundId).get("destination_holder")).isEqualTo(ORIGIN_HOLDER);
    }

    @ParameterizedTest
    @CsvSource({"ALUMNO,/students/{id}/refund-requests,403", "PRECEPTOR,/students/{id}/refund-requests,403", "DOCENTE,/students/{id}/refund-requests,403",
            "ADMINISTRATIVO,/me/refund-requests,403", "DIRECTOR,/me/refund-requests,403", "PRECEPTOR,/me/refund-requests,403", "DOCENTE,/me/refund-requests,403"})
    @Tag("D-159")
    @Tag("D-110")
    void onlyTheRightRolesCanRequestARefundOnEachEndpoint(String role, String path, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(refundRequest(path.replace("{id}", String.valueOf(debtor.studentId())), token, "10.00", REQUEST_ACCOUNT, REQUEST_HOLDER))
                .andExpect(status().is(expectedStatus));

        assertThat(jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isZero();
    }

    @Test
    @Tag("D-159")
    @Tag("D-110")
    void aStudentCannotCompleteNorFailARefundAndAnotherStudentCannotSeeTheirAccountThroughMe() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "10000.00")).andReturn()).get("parts").get(0).get("id").asLong();
        String student = studentTokenOf(debtor);

        mockMvc.perform(completion(refundId, student, "2027-03-09", "REF", "CASH")).andExpect(status().isForbidden());
        mockMvc.perform(failure(refundId, student, "motivo")).andExpect(status().isForbidden());
        mockMvc.perform(completion(refundId, staffToken("PRECEPTOR"), "2027-03-09", "REF", "CASH")).andExpect(status().isForbidden());

        assertThat(refundStatus(refundId)).isEqualTo("APPROVED");
        Debtor other = debtor();
        MvcResult otherResult = mockMvc.perform(refundRequest("/me/refund-requests", studentTokenOf(other), "10.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn();
        assertThat(otherResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(otherResult)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
    }

    @Test
    @Tag("D-159")
    void aPendingRequestReservesItsShareSoASecondRequestCannotTakeTheSameMoney() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");

        mockMvc.perform(requestOnBehalf(debtor, administrative, "60000.00")).andExpect(status().isAccepted());
        MvcResult second = mockMvc.perform(requestOnBehalf(debtor, administrative, "60000.00")).andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(second)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        mockMvc.perform(requestOnBehalf(debtor, administrative, "40000.00")).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-159")
    void twoSimultaneousRequestsOfSixtyPercentOfTheCreditLetOnlyOneThrough() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> request = () -> {
                start.await();
                return mockMvc.perform(requestOnBehalf(debtor, administrative, "60000.00")).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(202, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void withdrawingTheRequestRejectsItsPartsAndFreesTheReservedCredit() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        MvcResult pending = mockMvc.perform(requestOnBehalf(debtor, administrative, "80000.00")).andExpect(status().isAccepted()).andReturn();
        long authorizationRequestId = bodyOf(pending).get("authorizationRequestId").asLong();
        long refundId = bodyOf(pending).get("parts").get(0).get("id").asLong();
        assertThat(mockMvc.perform(requestOnBehalf(debtor, administrative, "80000.00")).andReturn().getResponse().getStatus()).isEqualTo(409);

        mockMvc.perform(decision("withdraw", authorizationRequestId, administrative)).andExpect(status().isOk());

        assertThat(authorizationStatus(authorizationRequestId)).isEqualTo("WITHDRAWN");
        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(refundRow(refundId).get("rejection_reason").toString()).contains("retirado");
        mockMvc.perform(requestOnBehalf(debtor, administrative, "80000.00")).andExpect(status().isAccepted());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    @Tag("D-216")
    void ifTheCreditWasUsedBeforeTheDirectorApprovesTheRequestIsRejectedOnResumeAndItsPartsAreRejected() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "70000.00")).andReturn()).get("authorizationRequestId").asLong();
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);
        inTransaction(() -> {
            service.consumeUnallocatedCreditForAllocation(service.lockStudentCurrentAccount(debtor.accountId()), NonNegativeMoney.of(ars("50000.00")));
            return null;
        });

        MvcResult result = mockMvc.perform(decision("approve", authorizationRequestId, staffToken("DIRECTOR"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select resume_outcome from authorization_request where id = ?", String.class, authorizationRequestId)).isEqualTo("REJECTED_ON_RESUME");
    }

    @Test
    @Tag("D-216")
    void aStudentWhoAlsoHoldsTheDirectorRoleStillWaitsForApprovalWhenRequestingOwnRefund() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        studentTokenOf(debtor);
        String studentAndDirector = tokenOf(personOf(debtor), "DIRECTOR");

        MvcResult result = mockMvc.perform(refundRequest("/me/refund-requests", studentAndDirector, "10000.00", REQUEST_ACCOUNT, REQUEST_HOLDER)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(bodyOf(result).get("parts").get(0).get("status").asText()).isEqualTo("REQUESTED");
    }
}
