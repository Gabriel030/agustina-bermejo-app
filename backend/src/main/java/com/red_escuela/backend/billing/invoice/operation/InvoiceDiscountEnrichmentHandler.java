package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PreProcessingHandler;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import org.springframework.stereotype.Component;

@Component
public class InvoiceDiscountEnrichmentHandler implements PreProcessingHandler<InvoiceIssuingContext> {

    private final InvoiceDiscountSelector selector;

    public InvoiceDiscountEnrichmentHandler(InvoiceDiscountSelector selector) {
        this.selector = selector;
    }

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, InvoiceIssuingContext context, PriorAuthorization priorAuthorization) {
        selector.selectDiscountsWithoutLock(context);
        return PreProcessingResult.proceed();
    }
}
