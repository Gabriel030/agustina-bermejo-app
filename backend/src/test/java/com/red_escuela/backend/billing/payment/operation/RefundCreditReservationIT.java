package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs HTTP refund scenarios with real threads against the shared database, and its time includes the one-time Spring context start-up")
class RefundCreditReservationIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);
    private static final LocalDate APRIL_DUE = LocalDate.of(2027, 4, 10);

    @Autowired
    CurrentAccountAllocationService allocationService;

    @Autowired
    CashPaymentPreviewService previewService;

    @Autowired
    AuthorizationRequestExpirer expirer;

    private void allocateAccountCredit(Debtor debtor) {
        inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(debtor.accountId()), TODAY, actor));
    }

    private void waitUntilSessionsAreBlockedOnLocks(int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long blocked = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class);
            if (blocked != null && blocked >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Expected " + expected + " sessions blocked on locks");
    }

    private void breakCreditTraceabilityOf(Debtor debtor) {
        jdbc.update("update student_current_account set unallocated_credit_amount = unallocated_credit_amount + 1000.00 where id = ?", debtor.accountId());
    }

    private long administrativeRequest(Debtor debtor, String token, String amount) throws Exception {
        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, token, amount)).andExpect(status().isAccepted()).andReturn();
        return bodyOf(result).get("authorizationRequestId").asLong();
    }

    private long directorApprovedRefund(Debtor debtor, String amount) throws Exception {
        MvcResult result = mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), amount)).andExpect(status().isCreated()).andReturn();
        return bodyOf(result).get("parts").get(0).get("id").asLong();
    }

    private MockHttpServletRequestBuilder revocation(long paymentId, String token) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobada por error\"}");
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, String token, String amount) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"2027-03-09\",\"reference\":\"BANCO-1\"}");
    }

    @Test
    @Tag("D-265")
    @Tag("D-134")
    void aPendingRefundRequestReservesItsAmountSoANewInvoiceIsNotPaidWithItUntilTheDirectorRejects() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "70000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);

        allocateAccountCredit(debtor);

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        mockMvc.perform(decision("reject", authorizationRequestId, staffToken("DIRECTOR"))).andExpect(status().isOk());
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    void onlyTheCreditNotReservedByTheRefundPaysTheNewInvoice() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "70000.00");
        Long fitsInTheFreePart = invoiceOf(debtor, "30000.00", APRIL_DUE);
        Long exceedsTheFreePart = invoiceOf(debtor, "30000.01", APRIL_DUE.plusDays(1));

        allocateAccountCredit(debtor);

        assertThat(paymentStatusOfInvoice(fitsInTheFreePart)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(exceedsTheFreePart)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("70000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    @Tag("D-159")
    void aRefundApprovedBeforeTheNewInvoiceCanStillBeCompletedBecauseItsCreditWasReserved() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = directorApprovedRefund(debtor, "80000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        allocateAccountCredit(debtor);

        MvcResult completed = mockMvc.perform(completion(refundId, staffToken("ADMINISTRATIVO"), "2027-03-09", "REF-1", "CASH")).andReturn();

        assertThat(completed.getResponse().getStatus()).isEqualTo(200);
        assertThat(refundStatus(refundId)).isEqualTo("COMPLETED");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("20000.00");
        assertThat(amount("select refunded_amount from payment where id = ?", paymentId)).isEqualByComparingTo("80000.00");
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    void aFailedRefundReleasesItsReservationAndTheCreditPaysTheOpenInvoice() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = directorApprovedRefund(debtor, "80000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");

        mockMvc.perform(failure(refundId, staffToken("ADMINISTRATIVO"), "Cuenta de destino cerrada")).andExpect(status().isOk());

        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    void aWithdrawnRequestReleasesItsReservationAndTheCreditPaysTheOpenInvoice() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        long authorizationRequestId = administrativeRequest(debtor, administrative, "70000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");

        mockMvc.perform(decision("withdraw", authorizationRequestId, administrative)).andExpect(status().isOk());

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    void theCashPaymentPreviewDoesNotCountTheReservedCreditAsAvailable() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "90000.00");
        invoiceOf(debtor, "50000.00", APRIL_DUE);

        CashPaymentPreview preview = inTransaction(() -> previewService.previewCashPaymentOfStudent(debtor.studentId(), ars("10000.00")));

        assertThat(preview.coveredInvoices()).isEmpty();
        assertThat(preview.coveredByRemainingCredit()).isEmpty();
        assertThat(preview.unallocatedAfterwards().value().amount()).isEqualByComparingTo("20000.00");
    }

    private String raceBetweenAnInvoiceAllocationAndARefundRequest(boolean theRequestArrivesFirst) throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "60000.00", APRIL_DUE);
        String director = staffToken("DIRECTOR");
        CountDownLatch accountIsHeld = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> inTransaction(() -> {
                service.lockStudentCurrentAccount(debtor.accountId());
                accountIsHeld.countDown();
                try {
                    releaseAccount.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            accountIsHeld.await();
            Callable<Integer> request = () -> mockMvc.perform(requestOnBehalf(debtor, director, "60000.00")).andReturn().getResponse().getStatus();
            Callable<Integer> allocation = () -> {
                allocateAccountCredit(debtor);
                return 0;
            };
            Future<Integer> first = executor.submit(theRequestArrivesFirst ? request : allocation);
            waitUntilSessionsAreBlockedOnLocks(1);
            Future<Integer> second = executor.submit(theRequestArrivesFirst ? allocation : request);
            waitUntilSessionsAreBlockedOnLocks(2);
            releaseAccount.countDown();
            holder.get();
            int requestStatus = theRequestArrivesFirst ? first.get() : second.get();
            long refunds = jdbc.queryForObject("select count(*) from refund where student_current_account_id = ?", Long.class, debtor.accountId());
            invariants.assertAllBillingInvariantsHold();
            return requestStatus + "/" + refunds + "/" + paymentStatusOfInvoice(april);
        } finally {
            releaseAccount.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-265")
    @Tag("D-134")
    void whenTheRefundRequestReachesTheAccountFirstItsCreditIsNotTakenByTheInvoice() throws Exception {
        assertThat(raceBetweenAnInvoiceAllocationAndARefundRequest(true)).isEqualTo("201/1/PENDING");
    }

    @Test
    @Tag("D-265")
    @Tag("D-134")
    void whenTheInvoiceAllocationReachesTheAccountFirstTheRefundRequestIsRejectedForInsufficientCredit() throws Exception {
        assertThat(raceBetweenAnInvoiceAllocationAndARefundRequest(false)).isEqualTo("409/0/PAID");
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    void aChargebackAndARevocationAreBlockedWhileThePaymentHasARequestedOrApprovedPart() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        String administrative = staffToken("ADMINISTRATIVO");
        String director = staffToken("DIRECTOR");
        long requestId = administrativeRequest(debtor, administrative, "40000.00");
        long entriesBefore = jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, debtor.accountId());

        MvcResult chargebackWhileRequested = mockMvc.perform(chargeback(paymentId, administrative, "100000.00")).andReturn();
        MvcResult revocationWhileRequested = mockMvc.perform(revocation(paymentId, director)).andReturn();
        mockMvc.perform(decision("approve", requestId, director)).andExpect(status().isOk());
        MvcResult chargebackWhileApproved = mockMvc.perform(chargeback(paymentId, administrative, "100000.00")).andReturn();
        MvcResult revocationWhileApproved = mockMvc.perform(revocation(paymentId, director)).andReturn();

        for (MvcResult blocked : List.of(chargebackWhileRequested, revocationWhileRequested, chargebackWhileApproved, revocationWhileApproved)) {
            assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
            assertThat(codeOf(blocked)).isEqualTo("PAYMENT_HAS_PENDING_REFUND");
        }
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(entriesBefore);
        assertThat(jdbc.queryForObject("select count(*) from chargeback where payment_id = ?", Long.class, paymentId)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    void onceThePendingPartIsClosedTheChargebackIsAcceptedAgain() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = directorApprovedRefund(debtor, "40000.00");
        mockMvc.perform(failure(refundId, staffToken("ADMINISTRATIVO"), "Cuenta de destino cerrada")).andExpect(status().isOk());

        mockMvc.perform(chargeback(paymentId, staffToken("ADMINISTRATIVO"), "100000.00")).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("CHARGED_BACK");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    @Tag("D-094")
    void aChargebackReopensMoreInvoicesWhenAnotherPaymentHasReservedCreditThatTheNetCreditCannotCover() throws Exception {
        Debtor debtor = debtor();
        long reservedPayment = cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long reversedPayment = cashPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        directorApprovedRefund(debtor, "100000.00");
        assertThat(jdbc.queryForObject("select count(*) from refund where payment_id = ?", Long.class, reservedPayment)).isEqualTo(1L);

        mockMvc.perform(chargeback(reversedPayment, staffToken("ADMINISTRATIVO"), "100000.00")).andExpect(status().isCreated());

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    @Tag("D-134")
    void anAllocationMadeWhileTheNewestPaymentIsReservedIsFundedByTheOldestPayment() throws Exception {
        Debtor debtor = debtor();
        long oldest = cashPayment(debtor, "100000.00", EARLY_MARCH);
        long newest = cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        directorApprovedRefund(debtor, "100000.00");
        assertThat(jdbc.queryForObject("select payment_id from refund where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(newest);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);

        allocateAccountCredit(debtor);

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(jdbc.queryForList("select s.payment_id from payment_allocation_source s join payment_allocation a on a.id = s.payment_allocation_id where a.invoice_id = ?",
                Long.class, april)).containsExactly(oldest);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    void aFailureToAllocateTheReleasedCreditDoesNotUndoTheRejection() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "70000.00");
        long refundId = jdbc.queryForObject("select id from refund where authorization_request_id = ?", Long.class, authorizationRequestId);
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        breakCreditTraceabilityOf(debtor);

        mockMvc.perform(decision("reject", authorizationRequestId, staffToken("DIRECTOR"))).andExpect(status().isOk());

        assertThat(refundStatus(refundId)).isEqualTo("REJECTED");
        assertThat(authorizationStatus(authorizationRequestId)).isEqualTo("REJECTED");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-265")
    void aFailureToAllocateTheCreditOfAFailedRefundDoesNotUndoTheFailure() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long refundId = directorApprovedRefund(debtor, "80000.00");
        invoiceOf(debtor, "50000.00", APRIL_DUE);
        breakCreditTraceabilityOf(debtor);

        mockMvc.perform(failure(refundId, staffToken("ADMINISTRATIVO"), "Cuenta de destino cerrada")).andExpect(status().isOk());

        assertThat(refundStatus(refundId)).isEqualTo("FAILED");
    }

    @Test
    @Tag("D-265")
    @Tag("D-159")
    void anExpiredRequestReleasesItsReservationAndTheCreditPaysTheOpenInvoice() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "70000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        jdbc.update("update authorization_request set expires_on = ? where id = ?", TODAY.minusDays(1), authorizationRequestId);

        inTransaction(expirer::expireDueRequests);

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("50000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    @Tag("D-159")
    void aFailureToAllocateOneAccountDoesNotStopTheBatchExpirationOfTheOthers() throws Exception {
        Debtor broken = debtor();
        cashPayment(broken, "100000.00", EARLY_MARCH);
        long brokenRequest = administrativeRequest(broken, staffToken("ADMINISTRATIVO"), "70000.00");
        invoiceOf(broken, "50000.00", APRIL_DUE);
        breakCreditTraceabilityOf(broken);
        Debtor healthy = debtor();
        cashPayment(healthy, "100000.00", EARLY_MARCH);
        long healthyRequest = administrativeRequest(healthy, staffToken("ADMINISTRATIVO"), "70000.00");
        Long healthyInvoice = invoiceOf(healthy, "50000.00", APRIL_DUE);
        jdbc.update("update authorization_request set expires_on = ? where id in (?, ?)", TODAY.minusDays(1), brokenRequest, healthyRequest);

        int expired = inTransaction(expirer::expireDueRequests);

        assertThat(expired).isGreaterThanOrEqualTo(2);
        assertThat(authorizationStatus(brokenRequest)).isEqualTo("EXPIRED");
        assertThat(authorizationStatus(healthyRequest)).isEqualTo("EXPIRED");
        assertThat(paymentStatusOfInvoice(healthyInvoice)).isEqualTo("PAID");
    }

    @Test
    @Tag("D-265")
    @Tag("D-264")
    void aRequestRejectedWhenTheDirectorApprovesReleasesItsCreditToThePendingInvoice() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        long authorizationRequestId = administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "70000.00");
        Long overdue = issueAdministrativeFeeWithLedgerDebit(debtor.accountId(), "5000.00", TODAY.minusDays(1));

        MvcResult result = mockMvc.perform(decision("approve", authorizationRequestId, staffToken("DIRECTOR"))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("REFUND_BLOCKED_BY_OVERDUE_DEBT");
        assertThat(paymentStatusOfInvoice(overdue)).isEqualTo("PAID");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("95000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-265")
    @Tag("D-134")
    void aRegisteredCashPaymentDoesNotSpendTheReservedCreditAndItsResponseShowsTheNetCredit() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", EARLY_MARCH);
        administrativeRequest(debtor, staffToken("ADMINISTRATIVO"), "90000.00");
        Long april = invoiceOf(debtor, "50000.00", APRIL_DUE);
        String token = staffToken("ADMINISTRATIVO");
        String path = "/students/" + debtor.studentId() + "/cash-payments";
        String amountJson = "{\"amount\":{\"amount\":10000.00,\"currency\":\"ARS\"}";
        String fingerprint = bodyOf(mockMvc.perform(post(path + "/preview").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(amountJson + "}")).andExpect(status().isOk()).andReturn()).get("previewFingerprint").asText();

        MvcResult registered = mockMvc.perform(post(path).header("Authorization", "Bearer " + token).header("Idempotency-Key", nextKey())
                .contentType(MediaType.APPLICATION_JSON).content(amountJson + ",\"previewFingerprint\":\"" + fingerprint + "\"}")).andReturn();

        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("110000.00");
        assertThat(decimalOf(bodyOf(registered).get("unallocatedCredit"))).isEqualByComparingTo("20000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    @Tag("D-206")
    void aRevocationReopensMoreInvoicesWhenAnotherPaymentHasReservedCreditThatTheNetCreditCannotCover() throws Exception {
        Debtor debtor = debtor();
        transferPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long revokedPayment = transferPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        directorApprovedRefund(debtor, "100000.00");

        mockMvc.perform(revocation(revokedPayment, staffToken("DIRECTOR"))).andExpect(status().isOk());

        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PENDING");
        assertThat(unallocatedCredit(debtor.accountId())).isEqualByComparingTo("100000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-262")
    @Tag("D-265")
    void aChargebackThatEvenReversingEveryAllocationCannotCoverIsRejectedWithNothingChanged() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long chargedBackPayment = cashPayment(debtor, "100000.00", EARLY_MARCH);
        directorApprovedRefund(debtor, "100000.00");
        jdbc.update("update student_current_account set unallocated_credit_amount = 150000.00 where id = ?", debtor.accountId());
        long entriesBefore = jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, debtor.accountId());

        MvcResult result = mockMvc.perform(chargeback(chargedBackPayment, staffToken("ADMINISTRATIVO"), "100000.00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, chargedBackPayment)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from chargeback where payment_id = ?", Long.class, chargedBackPayment)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(entriesBefore);
    }

    @Test
    @Tag("D-262")
    @Tag("D-094")
    void withoutReservedCreditAShortChargebackStaysRejectedAndNeverReversesOtherAllocations() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long chargedBackPayment = cashPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        jdbc.update("update student_current_account set unallocated_credit_amount = 50000.00 where id = ?", debtor.accountId());

        MvcResult result = mockMvc.perform(chargeback(chargedBackPayment, staffToken("ADMINISTRATIVO"), "100000.00")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(result)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where student_current_account_id = ? and reverses_allocation_id is not null", Long.class,
                debtor.accountId())).isZero();
    }
}
