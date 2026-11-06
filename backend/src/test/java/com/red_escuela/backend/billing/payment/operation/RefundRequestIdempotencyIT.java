package com.red_escuela.backend.billing.payment.operation;

import tools.jackson.databind.JsonNode;
import com.red_escuela.backend.testsupport.SlowTest;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-216")
@Tag("D-159")
@SlowTest(reason = "Retries refund requests sequentially and from two real threads against the database")
class RefundRequestIdempotencyIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);

    private long refundsOf(Debtor debtor) {
        return jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId());
    }

    private MvcResult send(Debtor debtor, String token, String key, String amount) throws Exception {
        return mockMvc.perform(requestOnBehalf(debtor, token, amount).header("Idempotency-Key", key)).andReturn();
    }

    @Test
    void m2r01TheDirectorRetryWithTheSameKeyReturnsTheSameApprovedPartsWithoutCreatingMore() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");
        assertThat(refundsOf(debtor)).isZero();

        MvcResult first = send(debtor, director, "refund-r01", "40000.00");
        MvcResult retry = send(debtor, director, "refund-r01", "40000.00");

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        JsonNode firstBody = bodyOf(first);
        JsonNode retryBody = bodyOf(retry);
        assertThat(retryBody.get("parts")).hasSize(1);
        assertThat(retryBody.get("parts").get(0).get("id").asLong()).isEqualTo(firstBody.get("parts").get(0).get("id").asLong());
        assertThat(retryBody.get("parts").get(0).get("refundRequestId").asLong()).isEqualTo(firstBody.get("parts").get(0).get("refundRequestId").asLong());
        assertThat(refundsOf(debtor)).isEqualTo(1L);
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
    }

    @Test
    void m2r02TheAdministrativeRetryReturnsTheSamePendingRequestAndOpensASingleApproval() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");

        MvcResult first = send(debtor, administrative, "refund-r02", "40000.00");
        MvcResult retry = send(debtor, administrative, "refund-r02", "40000.00");

        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(retry.getResponse().getStatus()).isEqualTo(202);
        long requestId = bodyOf(first).get("authorizationRequestId").asLong();
        assertThat(bodyOf(retry).get("authorizationRequestId").asLong()).isEqualTo(requestId);
        assertThat(bodyOf(retry).get("parts")).hasSize(1);
        assertThat(refundsOf(debtor)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where operation_type = 'ISSUE_REFUND' and id = ?", Long.class, requestId)).isEqualTo(1L);
    }

    @Test
    void m2r03TheSameKeyWithAnotherAmountIsAStableConflictAndCreatesNothing() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");
        send(debtor, director, "refund-r03", "10000.00");

        MvcResult conflict = send(debtor, director, "refund-r03", "20000.00");

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(conflict)).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(refundsOf(debtor)).isEqualTo(1L);
    }

    @Test
    void m2r04WithoutAKeyEveryRequestCreatesItsOwnRefundAsBefore() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");

        MvcResult first = mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andReturn();
        MvcResult second = mockMvc.perform(requestOnBehalf(debtor, director, "10000.00")).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(refundsOf(debtor)).isEqualTo(2L);
    }

    @Test
    void m2r05AnInvalidKeyIsRejectedBeforeAnythingIsWritten() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String director = staffToken("DIRECTOR");

        MvcResult blank = send(debtor, director, " ", "10000.00");
        MvcResult tooLong = send(debtor, director, "k".repeat(101), "10000.00");

        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(codeOf(blank)).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(refundsOf(debtor)).isZero();
    }

    @Test
    void m2r06TwoThreadsWithTheSameKeyCreateExactlyOneRefundRequest() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> call = () -> {
                start.await();
                return send(debtor, administrative, "refund-r06", "60000.00");
            };
            Future<MvcResult> first = executor.submit(call);
            Future<MvcResult> second = executor.submit(call);
            start.countDown();
            MvcResult one = first.get(60, TimeUnit.SECONDS);
            MvcResult two = second.get(60, TimeUnit.SECONDS);

            assertThat(List.of(one.getResponse().getStatus(), two.getResponse().getStatus())).containsExactly(202, 202);
            assertThat(bodyOf(one).get("authorizationRequestId").asLong()).isEqualTo(bodyOf(two).get("authorizationRequestId").asLong());
        } finally {
            executor.shutdownNow();
        }
        assertThat(refundsOf(debtor)).isEqualTo(1L);
    }
}
