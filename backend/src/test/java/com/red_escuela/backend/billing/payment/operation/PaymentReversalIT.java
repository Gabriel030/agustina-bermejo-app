package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs sixteen full reversal scenarios over HTTP, one with two real threads, on a shared Spring context that may boot cold")
class PaymentReversalIT extends AbstractBankTransferReceiptHttpIT {

    @Autowired
    ApprovePaymentReceiptUseCase approveReceipt;
    @Autowired
    RegisterChargebackOperation chargebackOperation;

    private long approvedTransfer(Debtor debtor, List<Long> invoiceIds, String amount) {
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, invoiceIds, amount);
        approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
        return submitted.paymentId();
    }

    private BankTransferReceiptSubmissionResult submitOn(Debtor debtor, List<Long> invoiceIds, String amount) {
        return submit(command(debtor, invoiceIds, amount, TODAY, nextOperationNumber()));
    }

    private MockHttpServletRequestBuilder revocation(long paymentId, String token, String reason) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}");
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, String token, String amount, String date, String reference) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"" + date + "\",\"reference\":"
                        + (reference == null ? "null" : "\"" + reference + "\"") + "}");
    }

    private MockHttpServletRequestBuilder chargebackReversal(long chargebackId, String token) {
        return post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + token);
    }

    private String codeOf(MvcResult result) throws Exception {
        return bodyOf(result).get("code").asText();
    }

    private String paymentStatus(long paymentId) {
        return jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId);
    }

    private List<Map<String, Object>> ledger(Long accountId) {
        return jdbc.queryForList("select entry_type, amount, effective_date, credit_round, running_balance_side, running_balance_amount from current_account_entry "
                + "where student_current_account_id = ? order by sequence_number", accountId);
    }

    private long countOf(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    @ParameterizedTest
    @CsvSource({"ALUMNO,403", "PRECEPTOR,403", "DOCENTE,403", "ADMINISTRATIVO,403", "DIRECTOR,200"})
    @Tag("D-206")
    void onlyTheDirectorRevokesAnApproval(String role, int expectedStatus) throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        String token = role.equals("ALUMNO") ? studentTokenOf(debtor) : staffToken(role);

        mockMvc.perform(revocation(paymentId, token, "Aprobada por error")).andExpect(status().is(expectedStatus));

        assertThat(paymentStatus(paymentId)).isEqualTo(expectedStatus == 200 ? "REJECTED" : "APPROVED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo(expectedStatus == 200 ? "PENDING" : "PAID");
    }

    @Test
    @Tag("D-206")
    void revokingRequiresAReason() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");

        MvcResult result = mockMvc.perform(revocation(paymentId, staffToken("DIRECTOR"), "  ")).andExpect(status().isBadRequest()).andReturn();

        assertThat(codeOf(result)).isEqualTo("PAYMENT_REJECTION_REASON_REQUIRED");
        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
    }

    @Test
    @Tag("D-206")
    @Tag("D-094")
    @Tag("D-144")
    @Tag("D-244")
    @Tag("D-331")
    void theDirectorRevokesAnApprovalAfterTheDueDateAndTheInvoiceIsOverdueWithItsOriginalDueDateAndNoCreditNote() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 18));

        mockMvc.perform(revocation(paymentId, staffToken("DIRECTOR"), "Aprobada por error")).andExpect(status().isOk());

        assertThat(paymentStatus(paymentId)).isEqualTo("REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("OVERDUE");
        assertThat(dueDateOf(march)).isEqualTo(MARCH_DUE);
        List<Map<String, Object>> entries = ledger(debtor.accountId());
        assertThat(entries).extracting(entry -> entry.get("entry_type")).containsExactly("INVOICE_ISSUED", "PAYMENT_RECEIVED", "PAYMENT_REVERSED");
        assertThat((java.math.BigDecimal) entries.get(2).get("amount")).isEqualByComparingTo("80000.00");
        assertThat(countOf("select count(*) from payment_allocation where student_current_account_id = ? and reverses_allocation_id is not null", debtor.accountId())).isEqualTo(1L);
        assertThat(countOf("select count(*) from invoice where student_current_account_id = ? and document_type <> 'INVOICE'", debtor.accountId())).isZero();
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, debtor.accountId()))
                .isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    @Tag("D-144")
    @Tag("D-262")
    @Tag("D-206")
    @Tag("D-244")
    void aChargebackTwoMonthsLaterReversesTheThreeInvoicesNewestFirstAndLeavesTheAccountOwingTheTotal() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long installment = invoiceOf(debtor, "500000.00", MARCH_DUE);
        Long surcharge = invoiceOf(debtor, "100000.00", APRIL_DUE);
        Long enrollmentFee = invoiceOf(debtor, "500000.00", LocalDate.of(2027, 5, 10));
        long paymentId = approvedTransfer(debtor, List.of(installment, surcharge, enrollmentFee), "1100000.00");
        assertThat(paymentStatusOfInvoice(enrollmentFee)).isEqualTo("PAID");
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 5, 9));

        mockMvc.perform(chargeback(paymentId, staffToken("ADMINISTRATIVO"), "1100000.00", "2027-05-09", "BANCO-77")).andExpect(status().isCreated());

        assertThat(paymentStatus(paymentId)).isEqualTo("CHARGED_BACK");
        assertThat(jdbc.queryForList("select invoice_id from payment_allocation where student_current_account_id = ? and reverses_allocation_id is not null order by id",
                Long.class, debtor.accountId())).containsExactly(enrollmentFee, surcharge, installment);
        List<Map<String, Object>> entries = ledger(debtor.accountId());
        Map<String, Object> last = entries.get(entries.size() - 1);
        assertThat(last.get("entry_type")).isEqualTo("PAYMENT_REVERSED");
        assertThat((java.math.BigDecimal) last.get("amount")).isEqualByComparingTo("1100000.00");
        assertThat(last.get("running_balance_side")).isEqualTo("OWES");
        assertThat((java.math.BigDecimal) last.get("running_balance_amount")).isEqualByComparingTo("1100000.00");
        assertThat(countOf("select count(*) from chargeback where payment_id = ? and reversed_on is null", paymentId)).isEqualTo(1L);
        assertThat(countOf("select count(*) from invoice where student_current_account_id = ? and document_type <> 'INVOICE'", debtor.accountId())).isZero();
        assertThat(paymentStatusOfInvoice(installment)).isEqualTo("OVERDUE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    @Tag("D-206")
    @Tag("D-144")
    void reversingTheChargebackCreditsAgainWithTheReversalDateAndAllocatesTheOldestFirst() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march, april), "160000.00");
        MvcResult registered = mockMvc.perform(chargeback(paymentId, staffToken("ADMINISTRATIVO"), "160000.00", "2027-03-09", "BANCO-1")).andExpect(status().isCreated()).andReturn();
        long chargebackId = bodyOf(registered).get("chargebackId").asLong();
        LocalDate reversalDate = LocalDate.of(2027, 3, 25);
        MutableClock.INSTANCE.setDate(reversalDate);

        mockMvc.perform(chargebackReversal(chargebackId, staffToken("ADMINISTRATIVO"))).andExpect(status().isOk());

        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
        List<Map<String, Object>> entries = ledger(debtor.accountId());
        Map<String, Object> newCredit = entries.get(entries.size() - 1);
        assertThat(newCredit.get("entry_type")).isEqualTo("PAYMENT_RECEIVED");
        assertThat(newCredit.get("credit_round")).isEqualTo(2);
        assertThat(((java.sql.Date) newCredit.get("effective_date")).toLocalDate()).isEqualTo(reversalDate);
        assertThat(jdbc.queryForList("select invoice_id from payment_allocation where student_current_account_id = ? and reverses_allocation_id is null order by id",
                Long.class, debtor.accountId())).containsExactly(march, april, march, april);
        assertThat(jdbc.queryForObject("select effective_date from payment_allocation where student_current_account_id = ? and reverses_allocation_id is null "
                + "order by id desc limit 1", LocalDate.class, debtor.accountId())).isEqualTo(reversalDate);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select reversed_on from chargeback where id = ?", LocalDate.class, chargebackId)).isEqualTo(reversalDate);
        mockMvc.perform(chargebackReversal(chargebackId, staffToken("DIRECTOR"))).andExpect(status().isConflict());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-094")
    @Tag("D-206")
    void aPaymentWhoseChargebackWasReversedCanBeChargedBackAgain() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        MvcResult first = mockMvc.perform(chargeback(paymentId, staffToken("DIRECTOR"), "80000.00", "2027-03-09", "A")).andExpect(status().isCreated()).andReturn();
        mockMvc.perform(chargebackReversal(bodyOf(first).get("chargebackId").asLong(), staffToken("DIRECTOR"))).andExpect(status().isOk());

        mockMvc.perform(chargeback(paymentId, staffToken("DIRECTOR"), "80000.00", "2027-03-09", "B")).andExpect(status().isCreated());

        assertThat(paymentStatus(paymentId)).isEqualTo("CHARGED_BACK");
        assertThat(countOf("select count(*) from chargeback where payment_id = ?", paymentId)).isEqualTo(2L);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'", paymentId)).isEqualTo(2L);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isEqualTo(2L);
    }

    @Test
    @Tag("D-206")
    void twoParallelChargebacksOfTheSamePaymentRegisterOnlyOne() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Throwable>> attempts = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            String reference = "PAR-" + attempt;
            attempts.add(executor.submit(() -> {
                start.await();
                try {
                    pipeline.runOperation(chargebackOperation, new RegisterChargebackCommand(paymentId, ars("80000.00"), TODAY, reference, actor));
                    return null;
                } catch (RuntimeException rejected) {
                    return rejected;
                }
            }));
        }
        start.countDown();
        List<Throwable> failures = new java.util.ArrayList<>();
        for (Future<Throwable> attempt : attempts) {
            Throwable failure = attempt.get(60, TimeUnit.SECONDS);
            if (failure != null) {
                failures.add(failure);
            }
        }
        executor.shutdownNow();

        assertThat(failures).singleElement().isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThat(countOf("select count(*) from chargeback where payment_id = ?", paymentId)).isEqualTo(1L);
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_REVERSED'", paymentId)).isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.update("insert into chargeback (payment_id, student_current_account_id, amount, amount_currency, charged_back_on, registered_at, "
                + "registered_by_process) values (?, ?, 1, 'ARS', ?, now(), 'test')", paymentId, debtor.accountId(), TODAY)).hasMessageContaining("uk_chargeback_open_per_payment");
    }

    @Test
    @Tag("D-262")
    @Tag("D-094")
    void aPaymentWhoseMoneyWasPartlyRefundedCannotBeRevokedNorChargedBack() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "300000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "300000.00");
        jdbc.update("update payment set refunded_amount = 100000.00 where id = ?", paymentId);
        long entriesBefore = ledger(debtor.accountId()).size();

        MvcResult revoked = mockMvc.perform(revocation(paymentId, staffToken("DIRECTOR"), "Error")).andExpect(status().isConflict()).andReturn();
        MvcResult charged = mockMvc.perform(chargeback(paymentId, staffToken("DIRECTOR"), "300000.00", "2027-03-09", "X")).andExpect(status().isConflict()).andReturn();

        assertThat(codeOf(revoked)).isEqualTo("PAYMENT_ALREADY_REFUNDED");
        assertThat(codeOf(charged)).isEqualTo("PAYMENT_ALREADY_REFUNDED");
        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
        assertThat(ledger(debtor.accountId())).hasSize((int) entriesBefore);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(countOf("select count(*) from chargeback where payment_id = ?", paymentId)).isZero();
    }

    @Test
    @Tag("D-262")
    @Tag("D-244")
    void aChargebackForLessOrMoreThanTheTotalIsRejectedAndTheTotalOneIsAccepted() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "300000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "300000.00");
        String token = staffToken("ADMINISTRATIVO");

        MvcResult less = mockMvc.perform(chargeback(paymentId, token, "299999.99", "2027-03-09", "X")).andExpect(status().isConflict()).andReturn();
        MvcResult more = mockMvc.perform(chargeback(paymentId, token, "300000.01", "2027-03-09", "X")).andExpect(status().isConflict()).andReturn();

        assertThat(codeOf(less)).isEqualTo("CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT");
        assertThat(codeOf(more)).isEqualTo("CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT");
        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
        mockMvc.perform(chargeback(paymentId, token, "300000.00", "2027-03-09", "X")).andExpect(status().isCreated());
        assertThat(paymentStatus(paymentId)).isEqualTo("CHARGED_BACK");
    }

    @Test
    @Tag("D-206")
    void theChargebackFieldsAreValidatedAgainstTheirColumns() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");
        String token = staffToken("DIRECTOR");

        assertThat(codeOf(mockMvc.perform(chargeback(paymentId, token, "80000.00", "2027-03-09", null)).andExpect(status().isBadRequest()).andReturn()))
                .isEqualTo("CHARGEBACK_REFERENCE_INVALID");
        assertThat(codeOf(mockMvc.perform(chargeback(paymentId, token, "80000.00", "2027-03-09", "x".repeat(121))).andExpect(status().isBadRequest()).andReturn()))
                .isEqualTo("CHARGEBACK_REFERENCE_INVALID");
        assertThat(codeOf(mockMvc.perform(chargeback(paymentId, token, "80000.00", "2027-03-10", "ok")).andExpect(status().isBadRequest()).andReturn()))
                .isEqualTo("CHARGEBACK_DATE_INVALID");
        mockMvc.perform(chargeback(paymentId, token, "80000.00", "2027-03-09", "x".repeat(120))).andExpect(status().isCreated());
    }

    @Test
    @Tag("D-206")
    void aPreceptorAndAStudentCannotRegisterNorReverseAChargeback() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");

        mockMvc.perform(chargeback(paymentId, staffToken("PRECEPTOR"), "80000.00", "2027-03-09", "X")).andExpect(status().isForbidden());
        mockMvc.perform(chargeback(paymentId, studentTokenOf(debtor), "80000.00", "2027-03-09", "X")).andExpect(status().isForbidden());
        mockMvc.perform(chargebackReversal(1L, staffToken("PRECEPTOR"))).andExpect(status().isForbidden());
        assertThat(paymentStatus(paymentId)).isEqualTo("APPROVED");
    }

    @Test
    @Tag("D-206")
    void unknownPaymentAndChargebackAreNotFound() throws Exception {
        mockMvc.perform(revocation(999999999L, staffToken("DIRECTOR"), "x")).andExpect(status().isNotFound());
        mockMvc.perform(chargeback(999999999L, staffToken("DIRECTOR"), "1.00", "2027-03-09", "x")).andExpect(status().isNotFound());
        mockMvc.perform(chargebackReversal(999999999L, staffToken("DIRECTOR"))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"revocation", "chargeback"})
    @Tag("D-206")
    @Tag("D-089")
    @Tag("D-144")
    void idleCreditOfAnotherPaymentIsAllocatedToTheOldestInvoiceAfterTheReversal(String kind) throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "30000.00", APRIL_DUE);
        long reversedPayment = approvedTransfer(debtor, List.of(march), "80000.00");
        long otherPayment = approvedTransfer(debtor, List.of(april), "110000.00");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, debtor.accountId()))
                .isEqualByComparingTo("80000.00");

        String token = staffToken("DIRECTOR");
        if (kind.equals("revocation")) {
            mockMvc.perform(revocation(reversedPayment, token, "Error")).andExpect(status().isOk());
        } else {
            mockMvc.perform(chargeback(reversedPayment, token, "80000.00", "2027-03-09", "CB")).andExpect(status().isCreated());
        }

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select s.payment_id from payment_allocation_source s where s.payment_allocation_id = "
                + "(select max(id) from payment_allocation where invoice_id = ? and reverses_allocation_id is null)", Long.class, march)).isEqualTo(otherPayment);
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, debtor.accountId()))
                .isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-206")
    @Tag("D-331")
    void eachReopenedInvoiceUsesTheGracePeriodOfItsOwnDueDate() throws Exception {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, staff, 10, java.math.BigDecimal.valueOf(0.05), 30, 2, LocalDate.of(2029, 1, 1));
        LocalDate paymentDay = LocalDate.of(2028, 12, 1);
        MutableClock.INSTANCE.setDate(paymentDay);
        Debtor debtor = debtor();
        Long withoutGrace = invoiceOf(debtor, "80000.00", LocalDate.of(2028, 12, 10));
        Long withTenDaysOfGrace = invoiceOf(debtor, "80000.00", LocalDate.of(2029, 1, 10));
        BankTransferReceiptSubmissionResult submitted = submit(command(debtor, List.of(withoutGrace, withTenDaysOfGrace), "160000.00", paymentDay, nextOperationNumber()));
        approveReceipt.approve(submitted.receiptId(), staff, PaymentVerification.none());
        MutableClock.INSTANCE.setDate(LocalDate.of(2029, 1, 15));

        mockMvc.perform(revocation(submitted.paymentId(), staffToken("DIRECTOR"), "Error")).andExpect(status().isOk());

        assertThat(paymentStatusOfInvoice(withoutGrace)).isEqualTo("OVERDUE");
        assertThat(paymentStatusOfInvoice(withTenDaysOfGrace)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-206")
    void aSecondCreditOfThePaymentThatWasNotReversedIsRejectedByTheUniqueIndex() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = approvedTransfer(debtor, List.of(march), "80000.00");

        assertThatThrownBy(() -> inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(debtor.accountId()), paymentId,
                NonNegativeMoney.of(ars("1.00")), TODAY, "Duplicado"))).rootCause().hasMessageContaining("uk_current_account_entry_payment_credit");
        assertThat(countOf("select count(*) from current_account_entry where payment_id = ? and entry_type = 'PAYMENT_RECEIVED'", paymentId)).isEqualTo(1L);
    }

    private LocalDate dueDateOf(Long invoiceId) {
        return jdbc.queryForObject("select due_date from invoice where id = ?", LocalDate.class, invoiceId);
    }
}
