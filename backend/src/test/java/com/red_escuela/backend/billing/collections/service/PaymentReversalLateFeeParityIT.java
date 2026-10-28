package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.InvoiceImpact;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.LateFeeReinstatementImpact;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackOperation;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackOperation;
import com.red_escuela.backend.billing.payment.operation.RevokeBankTransferApprovalCommand;
import com.red_escuela.backend.billing.payment.operation.RevokeBankTransferApprovalOperation;
import com.red_escuela.backend.billing.payment.service.PaymentReversalPreviewService;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.ReversalEffectsParity;
import com.red_escuela.backend.billing.support.ReversalEffectsParity.Snapshot;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.function.Supplier;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-414")
@Tag("D-206")
@SlowTest(reason = "Scenarios that seed overdue invoices, annul late fee days by payment date and run the preview and then the real reversal against Postgres")
class PaymentReversalLateFeeParityIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private RegisterChargebackOperation chargebackOperation;
    @Autowired
    private ReverseChargebackOperation reverseChargebackOperation;
    @Autowired
    private RevokeBankTransferApprovalOperation revokeOperation;
    @Autowired
    private PaymentReversalPreviewService previewService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private record AnnulledMarch(Invoice origin, long paymentId) {
    }

    private AnnulledMarch marchAnnulledByAPaymentDatedThe28th(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 1));
        MutableClock.INSTANCE.setDate(d(year, 4, 2));
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(origin.getStudentCurrentAccountId(), NonNegativeMoney.of(ars("100000.00")));
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()),
                paymentId, NonNegativeMoney.of(ars("100000.00")), d(year, 3, 28), "Pago"));
        allocateOn(origin, d(year, 4, 2));
        awaitEventsDrained();
        MutableClock.INSTANCE.setDate(d(year, 4, 10));
        return new AnnulledMarch(origin, paymentId);
    }

    private PaymentReversalEffects readOnlyEffects(Long accountId, Supplier<PaymentReversalPreview> preview) {
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, accountId);
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        PaymentReversalPreview result = readOnly.execute(status -> preview.get());
        ReversalEffectsParity.assertThePreviewChangedNothing(jdbc, accountId, before);
        return result.effects();
    }

    private PaymentReversalEffects previewChargeback(AnnulledMarch march, int year) {
        return readOnlyEffects(march.origin().getStudentCurrentAccountId(),
                () -> previewService.previewChargebackRegistration(march.paymentId(), ars("100000.00"), d(year, 4, 10), "ref-" + march.paymentId()));
    }

    private Long chargeback(AnnulledMarch march, int year) {
        return pipeline.runOperation(chargebackOperation, new RegisterChargebackCommand(march.paymentId(), ars("100000.00"), d(year, 4, 10), "ref-" + march.paymentId(), ACTOR))
                .resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("authorization " + id)).chargebackId();
    }

    private void addIdleCredit(Invoice origin, String amount, LocalDate paidOn) {
        long extraPayment = BillingTestFixtures.seedApprovedPaymentRow(origin.getStudentCurrentAccountId(), NonNegativeMoney.of(ars(amount)));
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()),
                extraPayment, NonNegativeMoney.of(ars(amount)), paidOn, "Pago adelantado"));
    }

    @Test
    void aChargebackPredictsTheLateFeeDaysItReinstatesAndTheRealOperationIssuesExactlyThatDebitNote() {
        int year = 2421;
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(year);
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, march.origin().getStudentCurrentAccountId());
        long entriesBefore = jdbc.queryForObject("select count(*) from invoice where student_current_account_id = ?", Long.class, march.origin().getStudentCurrentAccountId());

        PaymentReversalEffects effects = previewChargeback(march, year);

        assertThat(jdbc.queryForObject("select count(*) from invoice where student_current_account_id = ?", Long.class, march.origin().getStudentCurrentAccountId()))
                .isEqualTo(entriesBefore);
        assertThat(effects.calculated()).isTrue();
        assertThat(effects.reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(march.origin().getId());
        assertThat(effects.reopenedInvoices().get(0).statusAfter()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(effects.lateFeeReinstatements()).containsExactly(new LateFeeReinstatementImpact(march.origin().getId(), d(year, 3, 29), d(year, 3, 31), 3,
                NonNegativeMoney.of(ars("600.00")), false));
        assertThat(effects.settledInvoices()).isEmpty();
        chargeback(march, year);
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, march.origin().getStudentCurrentAccountId(), before, effects);
    }

    @Test
    void aRevocationPredictsTheSameReinstatementAndMatchesTheRealOperation() {
        int year = 2422;
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(year);
        jdbc.update("update payment set method_type = 'BANK_TRANSFER', method_details = cast('{\"type\":\"BANK_TRANSFER\"}' as jsonb) where id = ?", march.paymentId());
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, march.origin().getStudentCurrentAccountId());

        PaymentReversalEffects effects = readOnlyEffects(march.origin().getStudentCurrentAccountId(), () -> previewService.previewApprovalRevocation(march.paymentId(), "Aprobada por error"));

        assertThat(effects.lateFeeReinstatements()).hasSize(1);
        assertThat(effects.lateFeeReinstatements().get(0).chargedDays()).isEqualTo(3);
        pipeline.runOperation(revokeOperation, new RevokeBankTransferApprovalCommand(march.paymentId(), "Aprobada por error", ACTOR));
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, march.origin().getStudentCurrentAccountId(), before, effects);
    }

    @Test
    void creditThatCoversTheOriginTheOlderLateFeeNoteAndTheReinstatedNoteSettlesAllAndThePredictionSaysSo() {
        int year = 2423;
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(year);
        addIdleCredit(march.origin(), "104000.00", d(year, 4, 3));
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, march.origin().getStudentCurrentAccountId());

        PaymentReversalEffects effects = previewChargeback(march, year);

        assertThat(effects.reopenedInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(march.origin().getId());
        assertThat(effects.settledInvoices()).extracting(InvoiceImpact::invoiceId).contains(march.origin().getId());
        assertThat(effects.lateFeeReinstatements()).hasSize(1);
        assertThat(effects.lateFeeReinstatements().get(0).settledByAccountCredit()).isTrue();
        chargeback(march, year);
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, march.origin().getStudentCurrentAccountId(), before, effects);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, march.origin().getId())).isEqualTo("PAID");
    }

    @Test
    void creditThatCoversTheOriginButNotTheNoteSettlesOnlyTheOriginAndTheNoteStaysOpen() {
        int year = 2424;
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(year);
        addIdleCredit(march.origin(), "100300.00", d(year, 4, 3));
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, march.origin().getStudentCurrentAccountId());

        PaymentReversalEffects effects = previewChargeback(march, year);

        assertThat(effects.settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(march.origin().getId());
        assertThat(effects.lateFeeReinstatements().get(0).settledByAccountCredit()).isFalse();
        chargeback(march, year);
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, march.origin().getStudentCurrentAccountId(), before, effects);
    }

    @Test
    void reversingTheChargebackPredictsWhichInvoicesTheRestoredCreditSettlesIncludingTheReinstatedNote() {
        int year = 2425;
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(year);
        Long chargebackId = chargeback(march, year);
        awaitEventsDrained();
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, march.origin().getStudentCurrentAccountId());

        PaymentReversalEffects effects = readOnlyEffects(march.origin().getStudentCurrentAccountId(), () -> previewService.previewChargebackReversal(chargebackId));

        assertThat(effects.calculated()).isTrue();
        assertThat(effects.settledInvoices()).extracting(InvoiceImpact::invoiceId).containsExactly(march.origin().getId());
        assertThat(effects.lateFeeReinstatements()).isEmpty();
        pipeline.runOperation(reverseChargebackOperation, new ReverseChargebackCommand(chargebackId, ACTOR));
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, march.origin().getStudentCurrentAccountId(), before, effects);
    }

    @Test
    void twoPendingCorrectionsOfTheSameOriginAreReinstatedLikeTheRealOperationEvenWhenTheFirstNoteSettlesTheOrigin() {
        int year = 2426;
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 1));
        reconcile(origin, d(year, 5, 1));
        MutableClock.INSTANCE.setDate(d(year, 5, 2));
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(origin.getStudentCurrentAccountId(), NonNegativeMoney.of(ars("100000.00")));
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()),
                paymentId, NonNegativeMoney.of(ars("100000.00")), d(year, 3, 28), "Pago"));
        allocateOn(origin, d(year, 5, 2));
        awaitEventsDrained();
        MutableClock.INSTANCE.setDate(d(year, 5, 10));
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction k join late_fee_charge c on c.id = k.late_fee_charge_id where c.origin_invoice_id = ?",
                Long.class, origin.getId())).isGreaterThanOrEqualTo(2L);
        addIdleCredit(origin, "100000.00", d(year, 5, 3));
        Snapshot before = ReversalEffectsParity.takeSnapshot(jdbc, origin.getStudentCurrentAccountId());

        PaymentReversalEffects effects = readOnlyEffects(origin.getStudentCurrentAccountId(),
                () -> previewService.previewChargebackRegistration(paymentId, ars("100000.00"), d(year, 5, 10), "ref-" + paymentId));

        assertThat(effects.lateFeeReinstatements().size()).isGreaterThanOrEqualTo(2);
        pipeline.runOperation(chargebackOperation, new RegisterChargebackCommand(paymentId, ars("100000.00"), d(year, 5, 10), "ref-" + paymentId, ACTOR));
        awaitEventsDrained();
        ReversalEffectsParity.assertThePredictionMatchesWhatTheRealOperationDid(jdbc, origin.getStudentCurrentAccountId(), before, effects);
    }
}
