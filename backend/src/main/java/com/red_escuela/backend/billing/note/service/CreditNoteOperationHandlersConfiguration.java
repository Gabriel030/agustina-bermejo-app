package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.authorization.service.DirectorAuthorizationHandler;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.OperationPreHandlers;
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
}
