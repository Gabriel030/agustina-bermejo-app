package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PreProcessingHandler;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoiceDiscountEnrichmentHandler implements PreProcessingHandler<InvoiceIssuingContext> {

    private final List<InvoiceDiscountCandidateStrategy> strategies;
    private final InvoiceDiscountCalculator calculator;

    public InvoiceDiscountEnrichmentHandler(List<InvoiceDiscountCandidateStrategy> strategies, InvoiceDiscountCalculator calculator) {
        this.strategies = List.copyOf(strategies);
        this.calculator = calculator;
    }

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, InvoiceIssuingContext context, PriorAuthorization priorAuthorization) {
        List<InvoiceDiscountCandidate> candidates = strategies.stream()
                .flatMap(strategy -> strategy.findDiscountCandidatesForInvoice(context).stream())
                .toList();
        context.recordDiscountSelection(candidates, calculator.selectBestDiscountForInvoice(context.request().invoiceType(), candidates));
        return PreProcessingResult.proceed();
    }
}
