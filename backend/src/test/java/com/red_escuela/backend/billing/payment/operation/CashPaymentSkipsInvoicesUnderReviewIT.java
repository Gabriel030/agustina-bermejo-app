package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-261")
class CashPaymentSkipsInvoicesUnderReviewIT extends AbstractPendingTransferBridgeIT {

    private BigDecimal unallocatedCreditOf(Debtor debtor) {
        return jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, debtor.accountId());
    }

    @Test
    void cashSkipsTheInvoiceUnderReviewAndPaysTheNextOldestOneInThePreviewAndInTheExecution() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        submitOn(debtor, List.of(march), "80000.00", "OP-CASH-SKIP");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");

        CashPaymentPreview preview = previewCashOf(debtor, "80000.00");
        CashPaymentRegistrationResult registered = registerCash(debtor, "80000.00", preview);

        assertThat(preview.coveredInvoices()).extracting(CashPaymentPreview.CoveredInvoice::invoiceId).containsExactly(april);
        assertThat(registered.allocatedInvoices()).extracting(CashPaymentRegistrationResult.AllocatedInvoice::invoiceId).containsExactly(april);
        assertThat(paymentStatusOfInvoice(april)).isEqualTo("PAID");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void cashThatOnlyCoversTheInvoiceUnderReviewStaysAsCreditAndPaysItOnceTheReceiptIsRejected() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-CASH-CREDIT");

        CashPaymentPreview preview = previewCashOf(debtor, "80000.00");
        registerCash(debtor, "80000.00", preview);

        assertThat(preview.coveredInvoices()).isEmpty();
        assertThat(preview.coveredByRemainingCredit()).isEmpty();
        assertThat(preview.unallocatedAfterwards().value().amount()).isEqualByComparingTo("80000.00");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("80000.00");
        inTransaction(() -> allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(debtor.accountId()), LocalDate.of(2027, 3, 9), actor));
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("80000.00");

        reject(submitted, "Falta el titular");

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("0");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-320")
    void aReceiptRejectedForADifferentTransferDateAlsoReleasesAndAllocatesTheIdleCredit() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-CASH-DATE");
        registerCash(debtor, "80000.00", previewCashOf(debtor, "80000.00"));
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("80000.00");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> approve(submitted,
                new com.red_escuela.backend.billing.operation.PaymentVerification(null, LocalDate.of(2027, 3, 8), null)))
                .isInstanceOf(com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException.class);

        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("0");
    }

    @Autowired
    RegisterGatewayPaymentOperation gatewayOperation;

    @Test
    @Tag("D-261")
    void aGatewayPaymentLeavesTheInvoiceUnderReviewUntouchedAndKeepsTheMoneyAsCreditWithoutError() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        submitOn(debtor, List.of(march), "80000.00", "OP-GATEWAY-SKIP");

        RegisterGatewayPaymentCommand command = new RegisterGatewayPaymentCommand(debtor.accountId(), march, java.util.Set.of(), ars("80000.00"),
                com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode.DLOCAL,
                new com.red_escuela.backend.billing.payment.domain.CardPaymentDetails(com.red_escuela.backend.billing.operation.CardPaymentType.CREDIT, 1, null),
                "gw-skip-1", new com.red_escuela.backend.billing.payment.domain.GatewayRawStatus("approved", "accredited"), actor);
        pipeline.runOperation(gatewayOperation, command);

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(unallocatedCreditOf(debtor)).isEqualByComparingTo("80000.00");
    }
}
