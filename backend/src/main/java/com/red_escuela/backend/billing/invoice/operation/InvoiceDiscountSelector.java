package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class InvoiceDiscountSelector {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDiscountSelector.class);

    private final InvoiceDiscountStrategiesByOrigin strategies;
    private final InvoiceDiscountCalculator calculator;

    public InvoiceDiscountSelector(List<InvoiceDiscountCandidateStrategy> strategies, InvoiceDiscountCalculator calculator) {
        this.calculator = calculator;
        this.strategies = new InvoiceDiscountStrategiesByOrigin(strategies);
    }

    public void selectDiscountsWithoutLock(InvoiceIssuingContext context) {
        context.attachDiscountReselection(() -> reselectDiscountsUnderEnrollmentLock(context));
        selectDiscounts(context, strategy -> strategy.findDiscountCandidatesForInvoice(context));
    }

    public void reselectDiscountsUnderEnrollmentLock(InvoiceIssuingContext context) {
        selectDiscounts(context, strategy -> strategy.findDiscountCandidatesUnderEnrollmentLock(context));
    }

    private void selectDiscounts(InvoiceIssuingContext context, Function<InvoiceDiscountCandidateStrategy, List<InvoiceDiscountCandidate>> finder) {
        List<InvoiceDiscountCandidate> candidates = strategies.all().flatMap(strategy -> finder.apply(strategy).stream()).toList();
        Map<Integer, InvoiceDiscountCandidate> winners = calculator.selectBestDiscountPerInvoiceLine(context.request().invoiceType(), candidates);
        context.recordDiscountSelection(candidates, winners);
        candidates.forEach(candidate -> log.debug("Candidato de descuento: renglon={}, origen={}, tipo={}, ahorro={}, prioridad={}, otorgado={}, descuentoDeAlumno={}",
                candidate.lineNumber(), candidate.origin(), candidate.discountType(), candidate.discountAmount().value(), candidate.priority(), candidate.grantedAt(),
                candidate.studentDiscountId()));
        winners.forEach((lineNumber, winner) -> log.info("Descuento elegido: renglon={}, origen={}, tipo={}, fuente={}, ahorro={}, rangoDeTipo={}, prioridad={}, otorgado={}, candidatos={}",
                lineNumber, winner.origin(), winner.discountType(), winner.sourceId(), winner.discountAmount().value(), winner.typeRank(), winner.priority(), winner.grantedAt(),
                candidates.size()));
    }
}
