package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundCompletionIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    private long approvedRefund(Debtor debtor, String amount) throws Exception {
        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), amount)).andExpect(status().isCreated()).andReturn();
        return bodyOf(result).get("parts").get(0).get("id").asLong();
    }

    private String paymentStatus(long paymentId) {
        return jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId);
    }

    private java.math.BigDecimal refundedOf(long paymentId) {
        return amount("select refunded_amount from payment where id = ?", paymentId);
    }

    @Test
    @Tag("D-159")
    @Tag("D-203")
    void thePaidCreditIsReturnedFromRequestedToCompletedWithItsLedgerDebitAndTheAccountEndsAtZero() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "560000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        long authorizationRequestId = bodyOf(mockMvc.perform(requestOnBehalf(debtor, administrative, "560000.00")).andExpect(status().isAccepted()).andReturn())
                .get("authorizationRequestId").asLong();
        mockMvc.perform(decision("approve", authorizationRequestId, staffToken("DIRECTOR"))).andExpect(status().isOk());
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);

        MvcResult result = mockMvc.perform(completion(refundId, administrative, "2027-03-09", "TRF-001", "CASH")).andExpect(status().isOk()).andReturn();

        assertThat(bodyOf(result).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(refundStatus(refundId)).isEqualTo("COMPLETED");
        assertThat(refundRow(refundId).get("completion_reference")).isEqualTo("TRF-001");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select refund_id from current_account_entry where entry_type = 'REFUND_PAID' and student_current_account_id = ?", Long.class,
                debtor.accountId())).isEqualTo(refundId);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("0.00");
        assertThat(amount("select balance_amount from student_current_account where id = ?", debtor.accountId())).isEqualByComparingTo("0.00");
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUNDED");
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("560000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-209")
    @Tag("D-159")
    void aPartiallyRefundedPaymentOnlyOffersWhatIsLeftAndIsRefundedWhenItReachesTheTotal() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "1000.00", EARLY_MARCH);
        cashPayment(debtor, "500.00", EARLY_MARCH.minusDays(40));
        String administrative = staffToken("ADMINISTRATIVO");
        long first = approvedRefund(debtor, "300.00");

        mockMvc.perform(completion(first, administrative, "2027-03-09", "REF-1", "CASH")).andExpect(status().isOk());

        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("300.00");
        MvcResult second = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), "900.00")).andExpect(status().isCreated()).andReturn();
        List<Long> parts = partIds(bodyOf(second));
        assertThat(parts).hasSize(2);
        assertThat(jdbc.queryForObject("select amount from refund where id = ?", java.math.BigDecimal.class, parts.get(0))).isEqualByComparingTo("700.00");
        assertThat(jdbc.queryForObject("select amount from refund where id = ?", java.math.BigDecimal.class, parts.get(1))).isEqualByComparingTo("200.00");
        for (Long part : parts) {
            mockMvc.perform(completion(part, administrative, "2027-03-09", "REF-" + part, "CASH")).andExpect(status().isOk());
        }
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUNDED");
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("1000.00");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("300.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    void aTransferPartCompletedAsCashIsRejectedWithNothingChanged() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "40000.00");

        MvcResult result = mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2027-03-09", "REF", "CASH")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("REFUND_METHOD_MISMATCH");
        assertThat(refundStatus(refundId)).isEqualTo("APPROVED");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("0.00");
        mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2027-03-09", "REF", "BANK_TRANSFER")).andExpect(status().isOk());
    }

    @Test
    @Tag("D-159")
    void theCompletionDateAndReferenceAreValidatedAtTheirLimits() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "1000.00");
        String administrative = staffToken("ADMINISTRATIVO");

        for (String[] invalid : new String[][]{{"2027-03-10", "REF"}, {"2027-02-28", "REF"}, {"2027-03-09", " "}, {"2027-03-09", "r".repeat(121)}}) {
            MvcResult result = mockMvc.perform(completion(refundId, administrative, invalid[0], invalid[1], "CASH")).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(codeOf(result)).isEqualTo("REFUND_COMPLETION_INVALID");
        }
        assertThat(refundStatus(refundId)).isEqualTo("APPROVED");
        mockMvc.perform(completion(refundId, administrative, "2027-03-09", "r".repeat(120), "CASH")).andExpect(status().isOk());
    }

    @Test
    @Tag("D-159")
    void aFailedRefundMovesNoMoneyAndCannotBeCompletedAfterwards() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "40000.00");
        String administrative = staffToken("ADMINISTRATIVO");

        MvcResult invalid = mockMvc.perform(failure(refundId, administrative, " ")).andReturn();
        mockMvc.perform(failure(refundId, administrative, "La cuenta destino no existe")).andExpect(status().isOk());

        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(invalid)).isEqualTo("REFUND_FAILURE_REASON_INVALID");
        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
        assertThat(refundRow(refundId).get("failure_reason")).isEqualTo("La cuenta destino no existe");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("0.00");
        MvcResult completeAfterwards = mockMvc.perform(completion(refundId, administrative, "2027-03-09", "REF", "CASH")).andReturn();
        assertThat(completeAfterwards.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(completeAfterwards)).isEqualTo("ILLEGAL_REFUND_STATUS_TRANSITION");
        MvcResult failAgain = mockMvc.perform(failure(refundId, administrative, "otra vez")).andReturn();
        assertThat(failAgain.getResponse().getStatus()).isEqualTo(409);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    void aRefundStillWaitingForTheDirectorCannotBeCompletedNorFailed() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        MvcResult pending = mockMvc.perform(requestOnBehalf(debtor, staffToken("ADMINISTRATIVO"), "1000.00")).andExpect(status().isAccepted()).andReturn();
        long refundId = bodyOf(pending).get("parts").get(0).get("id").asLong();
        String administrative = staffToken("ADMINISTRATIVO");

        assertThat(mockMvc.perform(completion(refundId, administrative, "2027-03-09", "REF", "CASH")).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mockMvc.perform(failure(refundId, administrative, "motivo")).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(mockMvc.perform(completion(refundId + 999999, administrative, "2027-03-09", "REF", "CASH")).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(refundStatus(refundId)).isEqualTo("REQUESTED");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
    }

    @Test
    @Tag("D-159")
    void completingTheSameRefundTwiceBooksTheDebitOnlyOnce() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "40000.00");
        String administrative = staffToken("ADMINISTRATIVO");

        mockMvc.perform(completion(refundId, administrative, "2027-03-09", "REF", "CASH")).andExpect(status().isOk());
        MvcResult second = mockMvc.perform(completion(refundId, administrative, "2027-03-09", "REF", "CASH")).andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isEqualTo(1L);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("60000.00");
    }

    @Test
    @Tag("D-159")
    void twoSimultaneousCompletionsOfTheSameRefundBookASingleDebit() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "40000.00");
        String administrative = staffToken("ADMINISTRATIVO");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> complete = () -> {
                start.await();
                return mockMvc.perform(completion(refundId, administrative, "2027-03-09", "REF", "CASH")).andReturn().getResponse().getStatus();
            };
            Future<Integer> first = executor.submit(complete);
            Future<Integer> second = executor.submit(complete);
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isEqualTo(1L);
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("40000.00");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("60000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    void ifTheCreditWasUsedAfterTheApprovalTheCompletionIsRejectedAndTheRefundStaysApproved() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "80000.00");
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(debtor.accountId());
            service.consumeUnallocatedCreditForAllocation(account, NonNegativeMoney.of(ars("50000.00")));
            return null;
        });

        MvcResult result = mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2027-03-09", "REF", "CASH")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(refundStatus(refundId)).isEqualTo("APPROVED");
        assertThat(refundedOf(paymentId)).isEqualByComparingTo("0.00");
        assertThat(entriesOfType(debtor.accountId(), "REFUND_PAID")).isZero();
    }

    @Test
    @Tag("D-159")
    @Tag("D-209")
    void aRefundOfTheBalanceOfAnOldPaymentIsCompletedInAnotherSchoolYearOnALeapDay() throws Exception {
        ApplicationTime.useClock(MutableClock.INSTANCE);
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 12, 20));
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", LocalDate.of(2027, 12, 1));
        long refundId = approvedRefund(debtor, "100000.00");

        MutableClock.INSTANCE.setDate(LocalDate.of(2028, 2, 29));
        mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2028-03-01", "REF", "CASH")).andExpect(status().isBadRequest());
        mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2028-02-29", "REF", "CASH")).andExpect(status().isOk());

        assertThat(refundRow(refundId).get("completed_on").toString()).isEqualTo("2028-02-29");
        assertThat(jdbc.queryForObject("select effective_date from current_account_entry where refund_id = ?", LocalDate.class, refundId)).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(paymentStatus(paymentId)).isEqualTo("REFUNDED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-159")
    void everyStatusChangeOfARefundIsRecordedWithItsActor() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = approvedRefund(debtor, "40000.00");
        mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2027-03-09", "REF", "CASH")).andExpect(status().isOk());

        List<String> events = jdbc.queryForList("select event from refund_status_change where refund_id = ? order by id", String.class, refundId);

        assertThat(events).containsExactly("REFUND_REQUESTED", "REFUND_APPROVED", "REFUND_COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from refund_status_change where refund_id = ? and actor_person_id is null", Long.class, refundId)).isZero();
    }
}
