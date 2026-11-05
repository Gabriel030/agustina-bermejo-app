package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
import com.red_escuela.backend.billing.note.policy.CancellationByFullCreditNotePolicy;
import com.red_escuela.backend.billing.note.policy.CancellationInvoiceNotUnderReviewPolicy;
import com.red_escuela.backend.billing.note.policy.CancellationJustificationPolicy;
import com.red_escuela.backend.billing.note.policy.CancellationReasonPolicy;
import com.red_escuela.backend.billing.note.policy.LateFeeDebitNoteAnnulmentContext;
import com.red_escuela.backend.billing.note.policy.LateFeeDebitNoteNotUnderPaymentReviewPolicy;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationContext;
import com.red_escuela.backend.billing.note.policy.SystemInitiatedCancellationPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteDestinationUnchangedPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteInvoiceNotUnderReviewPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteIssuingContext;
import com.red_escuela.backend.billing.note.policy.CreditNoteJustificationRequiredPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteLinesBelongToInvoicePolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteLinesWithinCreditableAmountPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteReasonAllowedPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteFullDebtRequiresCancellationPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteReferencesIssuedInvoicePolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteRefundWithinPaidAmountPolicy;
import com.red_escuela.backend.billing.note.policy.CreditNoteWithinOutstandingAmountPolicy;
import com.red_escuela.backend.billing.note.policy.WithdrawalLineReductionContext;
import com.red_escuela.backend.billing.note.policy.WithdrawalReductionInvoiceUnpaidPolicy;
import com.red_escuela.backend.billing.note.policy.WithdrawalReductionLeavesDebtPolicy;
import com.red_escuela.backend.billing.note.policy.WithdrawalReductionLinesUnchangedPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CreditNoteOperationHandlersConfiguration {

    @Bean
    public OperationPreHandlers issueCreditNotePreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.ISSUE_CREDIT_NOTE, List.of(directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers cancelInvoicePreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.CANCEL_INVOICE, List.of(directorAuthorizationHandler));
    }

    @Bean
    public OperationPreHandlers cancelInvoicesAfterWithdrawalPreHandlers(DirectorAuthorizationHandler directorAuthorizationHandler) {
        return new OperationPreHandlers(BillingOperationType.CANCEL_INVOICES_AFTER_WITHDRAWAL, List.of(directorAuthorizationHandler));
    }

    @Bean
    public PolicySet<InvoiceCancellationContext> invoiceCancellationPolicySet() {
        return PolicySet.named("INVOICE_CANCELLATION", List.of(
                new CancellationJustificationPolicy(),
                new CancellationReasonPolicy(),
                new SystemInitiatedCancellationPolicy(),
                new CancellationInvoiceNotUnderReviewPolicy(),
                new CancellationByFullCreditNotePolicy()));
    }

    @Bean
    public PolicySet<LateFeeDebitNoteAnnulmentContext> lateFeeAnnulmentOnCancellationPolicySet() {
        return PolicySet.named("LATE_FEE_ANNULMENT_ON_CANCELLATION", List.of(new LateFeeDebitNoteNotUnderPaymentReviewPolicy()));
    }

    @Bean
    public PolicySet<CreditNoteIssuingContext> creditNoteIssuingPolicySet() {
        return PolicySet.named("CREDIT_NOTE_ISSUING", List.of(
                new CreditNoteJustificationRequiredPolicy(),
                new CreditNoteReasonAllowedPolicy(),
                new CreditNoteReferencesIssuedInvoicePolicy(),
                new CreditNoteInvoiceNotUnderReviewPolicy(),
                new CreditNoteLinesBelongToInvoicePolicy(),
                new CreditNoteDestinationUnchangedPolicy(),
                new CreditNoteRefundWithinPaidAmountPolicy(),
                new CreditNoteWithinOutstandingAmountPolicy(),
                new CreditNoteLinesWithinCreditableAmountPolicy(),
                new CreditNoteFullDebtRequiresCancellationPolicy()));
    }

    @Bean
    public PolicySet<WithdrawalLineReductionContext> withdrawalLineReductionPolicySet() {
        return PolicySet.named("WITHDRAWAL_LINE_REDUCTION", List.of(
                new WithdrawalReductionInvoiceUnpaidPolicy(),
                new WithdrawalReductionLinesUnchangedPolicy(),
                new WithdrawalReductionLeavesDebtPolicy()));
    }
}
