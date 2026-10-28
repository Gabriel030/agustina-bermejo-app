package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.InvoiceImpact;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.service.PaymentReversalPreviewService;
import com.red_escuela.backend.billing.support.ReversalEffectsParity;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.billing.support.ReversalEffectsParity.Snapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("D-094")
@Tag("D-206")
@Tag("D-265")
class PaymentReversalEffectsParityIT extends AbstractRefundIT {

    private static final LocalDate EARLY_MARCH = LocalDate.of(2027, 3, 1);
    private static final LocalDate APRIL_DUE = LocalDate.of(2027, 4, 10);
    private static final String REFERENCE = "BANCO-1";

    @Autowired
    PaymentReversalPreviewService previewService;

    @Autowired
    CurrentAccountAllocationService allocationService;

    private void allocateAccountCredit(Debtor debtor) {
        inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(debtor.accountId()), TODAY, actor));
    }

    private void directorApprovedRefund(Debtor debtor, String amount) throws Exception {
        mockMvc.perform(requestOnBehalf(debtor, staffToken("DIRECTOR"), amount)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder chargeback(long paymentId, String amount) {
        return post("/payments/{id}/chargebacks", paymentId).header("Authorization", "Bearer " + staffToken("ADMINISTRATIVO")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"chargedBackOn\":\"2027-03-09\",\"reference\":\"" + REFERENCE + "\"}");
    }

    private MockHttpServletRequestBuilder revocation(long paymentId) {
        return post("/payments/{id}/approval-revocation", paymentId).header("Authorization", "Bearer " + staffToken("DIRECTOR")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Aprobada por error\"}");
    }

    private PaymentReversalPreview previewChargeback(long paymentId, String amount) {
        return inTransaction(() -> previewService.previewChargebackRegistration(paymentId, ars(amount), TODAY, REFERENCE));
    }

    private PaymentReversalPreview previewRevocation(long paymentId) {
        return inTransaction(() -> previewService.previewApprovalRevocation(paymentId, "Aprobada por error"));
    }

    private PaymentReversalPreview previewReversal(long chargebackId) {
        return inTransaction(() -> previewService.previewChargebackReversal(chargebackId));
    }

    private long registerChargeback(long paymentId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(chargeback(paymentId, amount)).andExpect(status().isCreated()).andReturn();
        return bodyOf(result).get("chargebackId").asLong();
    }

    private long reservedRefundScenarioOf(Debtor debtor, boolean transfers, long[] revokedPayment) throws Exception {
        long reserved = transfers ? transferPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5)) : cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        revokedPayment[0] = transfers ? transferPayment(debtor, "100000.00", EARLY_MARCH) : cashPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        directorApprovedRefund(debtor, "100000.00");
        return april;
    }

    private long stateFingerprint(Debtor debtor) {
        return jdbc.queryForObject("select (select count(*) from current_account_entry where student_current_account_id = ?) * 1000000"
                + " + (select count(*) from payment_allocation where student_current_account_id = ?) * 1000"
                + " + (select count(*) from chargeback where student_current_account_id = ?) + (select count(*) from invoice where student_current_account_id = ?) * 1000000000",
                Long.class, debtor.accountId(), debtor.accountId(), debtor.accountId(), debtor.accountId());
    }

    @Test
    void aSimpleChargebackPredictsTheReopenedInvoiceAndTheCreditReversedAndMatchesTheRealOperation() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = cashPayment(debtor, "80000.00", EARLY_MARCH);
        allocateAccountCredit(debtor);
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());
        long fingerprint = stateFingerprint(debtor);

        PaymentReversalPreview preview = previewChargeback(paymentId, "80000.00");

        assertThat(stateFingerprint(debtor)).isEqualTo(fingerprint);
        assertThat(preview.allowed()).isTrue();
        PaymentReversalEffects effects = preview.effects();
        assertThat(effects.calculated()).isTrue();
        assertThat(effects.reopenedInvoices()).containsExactly(new InvoiceImpact(march, InvoicePaymentStatus.PAID, InvoicePaymentStatus.PENDING,
                NonNegativeMoney.of(ars("0.00")), NonNegativeMoney.of(ars("80000.00")), NonNegativeMoney.of(ars("80000.00"))));
        assertThat(effects.settledInvoices()).isEmpty();
        assertThat(effects.lateFeeReinstatements()).isEmpty();
        registerChargeback(paymentId, "80000.00");
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    void whenTheCreditOfAnotherPaymentSettlesTheReopenedInvoiceAgainThePredictionListsItInBothLists() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long reversedPayment = cashPayment(debtor, "80000.00", EARLY_MARCH);
        cashPayment(debtor, "80000.00", LocalDate.of(2027, 3, 5));
        allocateAccountCredit(debtor);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());

        PaymentReversalEffects effects = previewChargeback(reversedPayment, "80000.00").effects();

        assertThat(effects.reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(march);
        assertThat(effects.settledInvoices()).hasSize(1);
        assertThat(effects.settledInvoices().get(0).invoiceId()).isEqualTo(march);
        assertThat(effects.settledInvoices().get(0).statusBefore()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(effects.settledInvoices().get(0).statusAfter()).isEqualTo(InvoicePaymentStatus.PAID);
        registerChargeback(reversedPayment, "80000.00");
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    @Tag("D-427")
    void aChargebackWithReservedCreditOfAnotherPaymentPredictsTheExtraReversalOfTheNewestAllocationAndMatchesTheRealOperation() throws Exception {
        Debtor debtor = debtor();
        long[] reversed = new long[1];
        long april = reservedRefundScenarioOf(debtor, false, reversed);
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());
        long fingerprint = stateFingerprint(debtor);

        PaymentReversalEffects effects = previewChargeback(reversed[0], "100000.00").effects();

        assertThat(stateFingerprint(debtor)).isEqualTo(fingerprint);
        assertThat(effects.reopenedInvoices()).hasSize(1);
        assertThat(effects.reopenedInvoices().get(0).invoiceId()).isEqualTo(april);
        assertThat(effects.reopenedInvoices().get(0).statusAfter()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(effects.reopenedInvoices().get(0).creditReversed().value().amount()).isEqualByComparingTo("100000.00");
        assertThat(effects.settledInvoices()).isEmpty();
        registerChargeback(reversed[0], "100000.00");
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    @Tag("D-427")
    void aRevocationWithReservedCreditOfAnotherPaymentPredictsTheExtraReversalAndMatchesTheRealOperation() throws Exception {
        Debtor debtor = debtor();
        long[] revoked = new long[1];
        long april = reservedRefundScenarioOf(debtor, true, revoked);
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());

        PaymentReversalEffects effects = previewRevocation(revoked[0]).effects();

        assertThat(effects.reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(april);
        assertThat(effects.reopenedInvoices().get(0).creditReversed().value().amount()).isEqualByComparingTo("100000.00");
        mockMvc.perform(revocation(revoked[0])).andExpect(status().isOk());
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    void aRevocationOfAFundingPaymentPredictsTheReopenedInvoicesNewestFirstSetAndMatchesTheRealOperation() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "60000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "40000.00", APRIL_DUE);
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);
        allocateAccountCredit(debtor);
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());

        PaymentReversalEffects effects = previewRevocation(paymentId).effects();

        assertThat(effects.reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactlyInAnyOrder(march, april);
        assertThat(effects.reopenedInvoices()).allMatch(impact -> impact.statusAfter() == InvoicePaymentStatus.PENDING);
        mockMvc.perform(revocation(paymentId)).andExpect(status().isOk());
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    void theReversalOfAChargebackPredictsTheInvoicesTheRestoredCreditSettlesAndMatchesTheRealOperation() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = cashPayment(debtor, "80000.00", EARLY_MARCH);
        allocateAccountCredit(debtor);
        long chargebackId = registerChargeback(paymentId, "80000.00");
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());
        long fingerprint = stateFingerprint(debtor);

        PaymentReversalPreview preview = previewReversal(chargebackId);

        assertThat(stateFingerprint(debtor)).isEqualTo(fingerprint);
        assertThat(preview.effects().calculated()).isTrue();
        assertThat(preview.effects().reopenedInvoices()).isEmpty();
        assertThat(preview.effects().settledInvoices()).containsExactly(new InvoiceImpact(march, InvoicePaymentStatus.PENDING, InvoicePaymentStatus.PAID,
                NonNegativeMoney.of(ars("80000.00")), NonNegativeMoney.of(ars("0.00")), NonNegativeMoney.of(ars("0.00"))));
        mockMvc.perform(post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + staffToken("ADMINISTRATIVO"))).andExpect(status().isOk());
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, preview.effects());
    }

    @Test
    void theReversalOfAChargebackWithCreditThatCoversNoInvoicePredictsNothingToSettle() throws Exception {
        Debtor debtor = debtor();
        long paymentId = cashPayment(debtor, "80000.00", EARLY_MARCH);
        long chargebackId = registerChargeback(paymentId, "80000.00");
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, debtor.accountId());

        PaymentReversalEffects effects = previewReversal(chargebackId).effects();

        assertThat(effects.calculated()).isTrue();
        assertThat(effects.settledInvoices()).isEmpty();
        mockMvc.perform(post("/chargebacks/{id}/reversal", chargebackId).header("Authorization", "Bearer " + staffToken("ADMINISTRATIVO"))).andExpect(status().isOk());
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, debtor.accountId(), before, effects);
    }

    @Test
    void aChargebackThatNoCreditCanCoverIsBlockedByInsufficientAccountCreditLikeTheRealOperationAndCalculatesNoEffects() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long chargedBackPayment = cashPayment(debtor, "100000.00", EARLY_MARCH);
        Long april = invoiceOf(debtor, "100000.00", APRIL_DUE);
        allocateAccountCredit(debtor);
        jdbc.update("update student_current_account set unallocated_credit_amount = 50000.00 where id = ?", debtor.accountId());

        PaymentReversalPreview preview = previewChargeback(chargedBackPayment, "100000.00");

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.blockedBy().code()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(preview.effects().calculated()).isFalse();
        MvcResult real = mockMvc.perform(chargeback(chargedBackPayment, "100000.00")).andReturn();
        assertThat(real.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(real)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
    }

    @Test
    void aChargebackWithReservedCreditThatEvenEveryReversalCannotCoverIsBlockedByInsufficientAccountCredit() throws Exception {
        Debtor debtor = debtor();
        cashPayment(debtor, "100000.00", LocalDate.of(2027, 3, 5));
        long chargedBackPayment = cashPayment(debtor, "100000.00", EARLY_MARCH);
        directorApprovedRefund(debtor, "100000.00");
        jdbc.update("update student_current_account set unallocated_credit_amount = 150000.00 where id = ?", debtor.accountId());

        PaymentReversalPreview preview = previewChargeback(chargedBackPayment, "100000.00");

        assertThat(preview.blockedBy().code()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(preview.effects().calculated()).isFalse();
        MvcResult real = mockMvc.perform(chargeback(chargedBackPayment, "100000.00")).andReturn();
        assertThat(codeOf(real)).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
    }

    @Test
    void aBlockedPreviewByAPolicyKeepsTheEffectsNotCalculated() throws Exception {
        Debtor debtor = debtor();
        long paymentId = transferPayment(debtor, "100000.00", EARLY_MARCH);

        PaymentReversalPreview preview = previewChargeback(paymentId, "99999.99");

        assertThat(preview.blockedBy().code()).isEqualTo("CHARGEBACK_MUST_BE_FOR_THE_FULL_AMOUNT");
        assertThat(preview.effects().calculated()).isFalse();
        assertThat(preview.effects().reopenedInvoices()).isEmpty();
    }

    @Test
    void theHttpPreviewExposesTheCalculatedEffectsWithTheirAmountsAndStatuses() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        long paymentId = cashPayment(debtor, "80000.00", EARLY_MARCH);
        allocateAccountCredit(debtor);

        MvcResult result = mockMvc.perform(post("/payments/{id}/chargebacks/preview", paymentId).header("Authorization", "Bearer " + staffToken("ADMINISTRATIVO"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":{\"amount\":80000.00,\"currency\":\"ARS\"},\"chargedBackOn\":\"2027-03-09\",\"reference\":\"" + REFERENCE + "\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode effects = bodyOf(result).get("effects");
        assertThat(effects.get("calculated").asBoolean()).isTrue();
        assertThat(effects.get("reopenedInvoices")).hasSize(1);
        JsonNode reopened = effects.get("reopenedInvoices").get(0);
        assertThat(reopened.get("invoiceId").asLong()).isEqualTo(march);
        assertThat(reopened.get("paymentStatusBefore").asText()).isEqualTo("PAID");
        assertThat(reopened.get("paymentStatusAfter").asText()).isEqualTo("PENDING");
        assertThat(decimalOf(reopened.get("outstandingBefore"))).isEqualByComparingTo("0.00");
        assertThat(decimalOf(reopened.get("outstandingAfter"))).isEqualByComparingTo("80000.00");
        assertThat(decimalOf(reopened.get("creditReversed"))).isEqualByComparingTo("80000.00");
        assertThat(effects.get("settledInvoices")).isEmpty();
        assertThat(effects.get("lateFeeReinstatements")).isEmpty();
    }

    @Test
    void theInvoicesTheRealChargebackReportsAsReopenedAreExactlyThePredictedOnes() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "60000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "40000.00", APRIL_DUE);
        long paymentId = cashPayment(debtor, "100000.00", EARLY_MARCH);
        allocateAccountCredit(debtor);

        List<Long> predicted = previewChargeback(paymentId, "100000.00").effects().reopenedInvoices().stream().map(InvoiceImpact::invoiceId).sorted().toList();
        JsonNode real = bodyOf(mockMvc.perform(chargeback(paymentId, "100000.00")).andExpect(status().isCreated()).andReturn());

        assertThat(predicted).containsExactly(march, april);
        assertThat(real.get("reopenedInvoiceIds")).extracting(JsonNode::asLong).containsExactlyInAnyOrderElementsOf(predicted);
    }
}
