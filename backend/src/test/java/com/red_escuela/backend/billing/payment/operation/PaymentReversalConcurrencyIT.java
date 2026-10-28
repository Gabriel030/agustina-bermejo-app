package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PaymentReversalConcurrencyIT extends AbstractRefundIT {

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;

    private long approvedTransfer(Debtor debtor, List<Long> invoiceIds, String amount) {
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, invoiceIds, amount, TODAY, nextOperationNumber()));
        approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
        return submitted.paymentId();
    }

    private MockHttpServletRequestBuilder revocation(long paymentId, String token) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobada por error\"}");
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, String token, String amount) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"2027-03-09\",\"reference\":\"BANCO-" + nextKey() + "\"}");
    }

    private MockHttpServletRequestBuilder chargebackReversal(long chargebackId, String token) {
        return post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + token);
    }

    private int send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    private long countOf(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private void waitUntilSessionsAreBlockedOnLocks(int expected) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> {
            Long blocked = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class);
            return blocked != null && blocked >= expected;
        });
    }

    private List<Integer> raceWhileTheAccountIsHeld(Debtor debtor, Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch accountIsHeld = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> inTransaction(() -> {
                service.lockStudentCurrentAccount(debtor.accountId());
                accountIsHeld.countDown();
                try {
                    releaseAccount.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(accountIsHeld.await(30, TimeUnit.SECONDS)).isTrue();
            long blockedBefore = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class);
            Future<Integer> firstResult = executor.submit(first);
            waitUntilSessionsAreBlockedOnLocks((int) blockedBefore + 1);
            Future<Integer> secondResult = executor.submit(second);
            waitUntilSessionsAreBlockedOnLocks((int) blockedBefore + 2);
            releaseAccount.countDown();
            holder.get(60, TimeUnit.SECONDS);
            return List.of(firstResult.get(60, TimeUnit.SECONDS), secondResult.get(60, TimeUnit.SECONDS));
        } finally {
            releaseAccount.countDown();
            executor.shutdownNow();
        }
    }

    private List<Integer> freeRace(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> attempt : List.of(first, second)) {
                results.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return attempt.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-206")
    @Tag("D-262")
    void aChargebackAndARevocationOfTheSameApprovalLeaveOneReversalOneCodedConflictAndTheAccountOwingOnce() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        String director = staffToken("DIRECTOR");

        List<Integer> statuses = raceWhileTheAccountIsHeld(debtor, () -> send(chargeback(paymentId, director, "80000.00")), () -> send(revocation(paymentId, director)));

        assertThat(statuses).filteredOn(status -> status >= 200 && status < 300).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(1);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(march)).isIn("PENDING", "OVERDUE");
        assertThat(amount("select balance_amount from student_current_account where id = ?", debtor.accountId())).isEqualByComparingTo("80000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-206")
    void aRevocationAndAChargebackInTheOppositeOrderAlsoLeaveASingleReversal() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        String director = staffToken("DIRECTOR");

        List<Integer> statuses = raceWhileTheAccountIsHeld(debtor, () -> send(revocation(paymentId, director)), () -> send(chargeback(paymentId, director, "80000.00")));

        assertThat(statuses.get(0)).isEqualTo(200);
        assertThat(statuses.get(1)).isEqualTo(409);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isEqualTo(1L);
        assertThat(countOf("select count(*) from chargeback where payment_id = ?", paymentId)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    void aChargebackAndARefundRequestOfTheSameCreditNeverBothSucceed() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", TODAY);
        String director = staffToken("DIRECTOR");

        List<Integer> statuses = raceWhileTheAccountIsHeld(debtor, () -> send(chargeback(paymentId, director, "100000.00")),
                () -> send(requestOnBehalf(debtor, director, "100000.00")));

        assertThat(statuses.get(0)).as("the chargeback arrived first").isEqualTo(201);
        assertThat(statuses.get(1)).as("the refund finds no credit left").isEqualTo(409);
        assertThat(countOf("select count(*) from refund where payment_id = ?", paymentId)).isZero();
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    void aRefundRequestAndAChargebackOfTheSameCreditInTheOppositeOrderBlockTheChargeback() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", TODAY);
        String director = staffToken("DIRECTOR");

        List<Integer> statuses = raceWhileTheAccountIsHeld(debtor, () -> send(requestOnBehalf(debtor, director, "100000.00")),
                () -> send(chargeback(paymentId, director, "100000.00")));

        assertThat(statuses.get(0)).isEqualTo(201);
        assertThat(statuses.get(1)).isEqualTo(409);
        assertThat(countOf("select count(*) from chargeback where payment_id = ?", paymentId)).isZero();
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-206")
    @Tag("D-094")
    void twoParallelReversalsOfTheSameChargebackRestoreTheCreditOnlyOnce() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        String director = staffToken("DIRECTOR");
        assertThat(send(chargeback(paymentId, director, "80000.00"))).isEqualTo(201);
        long chargebackId = countOf("select id from chargeback where payment_id = ?", paymentId);
        long creditsBefore = countOf("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", debtor.accountId());

        List<Integer> statuses = freeRace(() -> send(chargebackReversal(chargebackId, director)), () -> send(chargebackReversal(chargebackId, director)));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(countOf("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", debtor.accountId()))
                .isEqualTo(creditsBefore + 1);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-206")
    @Tag("D-144")
    void aChargebackOfOnePaymentWhileAnotherCashPaymentArrivesKeepsTheBalanceEqualToCreditsMinusDebits() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "60000.00", APRIL_DUE);
        long transferId = approvedTransfer(debtor, List.of(march), "80000.00");
        String director = staffToken("DIRECTOR");

        List<Integer> statuses = raceWhileTheAccountIsHeld(debtor, () -> send(chargeback(transferId, director, "80000.00")), () -> {
            cashPayment(debtor, "60000.00", TODAY);
            return 0;
        });

        assertThat(statuses.get(0)).isEqualTo(201);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", transferId)).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(march)).isIn("PENDING", "OVERDUE");
        assertThat(paymentStatusOfInvoice(april)).isIn("PENDING", "PAID");
        assertThat(amount("select balance_amount from student_current_account where id = ?", debtor.accountId())).isEqualByComparingTo("80000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-206")
    @Tag("D-262")
    void unorderedRacesBetweenChargebackAndRevocationNeverDeadlockNorEndInAServerError() throws Exception {
        for (int round = 0; round < 3; round++) {
            Debtor debtor = debtor();
            Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
            Long april = invoiceOf(debtor, "60000.00", APRIL_DUE);
            long paymentId = approvedTransfer(debtor, List.of(march, april), "140000.00");
            String director = staffToken("DIRECTOR");

            List<Integer> statuses = freeRace(() -> send(chargeback(paymentId, director, "140000.00")), () -> send(revocation(paymentId, director)));

            assertThat(statuses).as("round " + round).filteredOn(status -> status >= 200 && status < 300).hasSize(1);
            assertThat(statuses).filteredOn(status -> status >= 500).isEmpty();
            assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isEqualTo(1L);
        }
        invariants.assertAllBillingInvariantsHold();
    }
}
