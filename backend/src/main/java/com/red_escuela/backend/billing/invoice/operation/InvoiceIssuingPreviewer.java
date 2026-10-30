package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingPreview;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InvoiceIssuingPreviewer {
    private static final String ALREADY_ISSUED_CODE = "ALREADY_ISSUED";
    private static final Long PREVIEW_ACCOUNT_ID = 0L;

    private final InvoiceIssuingContextLoader contextLoader;
    private final PolicySet<InvoiceIssuingContext> issuingPolicies;
    private final InvoiceDiscountSelector discountSelector;

    public InvoiceIssuingPreviewer(InvoiceIssuingContextLoader contextLoader, PolicySet<InvoiceIssuingContext> issuingPolicies, InvoiceDiscountSelector discountSelector) {
        this.contextLoader = contextLoader;
        this.issuingPolicies = issuingPolicies;
        this.discountSelector = discountSelector;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InvoiceIssuingPreview previewInvoiceIssuing(InvoiceIssuingRequest request) {
        InvoiceIssuingContext context = contextLoader.loadContextLockingRecipient(request);
        PolicyEvaluationResult evaluation = issuingPolicies.evaluateRulesUntilFirstViolation(context);
        if (!evaluation.allowed()) {
            return previewOfRejection(context, evaluation);
        }
        discountSelector.selectDiscountsWithoutLock(context);
        Invoice draft = InvoiceDraftAssembler.assembleDraftInvoice(context, PREVIEW_ACCOUNT_ID);
        return new InvoiceIssuingPreview.InvoiceWouldBeIssued(draft.getAmounts().total());
    }

    private InvoiceIssuingPreview previewOfRejection(InvoiceIssuingContext context, PolicyEvaluationResult evaluation) {
        boolean alreadyIssued = context.chargeKeyAlreadyUsed() && evaluation.firstDenial().map(verdict -> ALREADY_ISSUED_CODE.equals(verdict.code())).orElse(false);
        return alreadyIssued
                ? new InvoiceIssuingPreview.InvoiceWouldBeAlreadyIssued(context.facts().existingInvoiceId())
                : new InvoiceIssuingPreview.InvoiceWouldBeRejected(evaluation);
    }
}
