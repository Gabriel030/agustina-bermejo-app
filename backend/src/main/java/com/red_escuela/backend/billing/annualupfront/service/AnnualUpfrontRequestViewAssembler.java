package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontQuoteLine;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.service.InvoiceWithLinesReader;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class AnnualUpfrontRequestViewAssembler {
    private final InvoiceWithLinesReader invoices;
    private final AnnualUpfrontRequestRepository requests;

    public AnnualUpfrontRequestViewAssembler(InvoiceWithLinesReader invoices, AnnualUpfrontRequestRepository requests) {
        this.invoices = invoices;
        this.requests = requests;
    }

    public AnnualUpfrontRequestView assembleViewOf(AnnualUpfrontRequest request) {
        Invoice invoice = invoices.findInvoiceWithLines(request.getInvoiceId()).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(request.getId()));
        List<InvoiceLine> invoiceLines = invoice.getLines().stream().sorted(Comparator.comparingInt(InvoiceLine::getLineNumber)).toList();
        List<AnnualUpfrontQuoteLine> lines = invoiceLines.stream().map(AnnualUpfrontRequestViewAssembler::quoteLineOf).toList();
        return new AnnualUpfrontRequestView(request.getId(), request.getStatus().name(), request.getProgramEnrollmentId(), request.getInvoiceId(),
                requests.findLectiveYearByTermId(request.getLectiveYearTermId()).orElse(null), invoice.getPaymentDeadline(), lines, invoice.getAmounts().total().value(),
                discountOf(invoice, invoiceLines), request.getAcceptedAt(), request.getAcceptedTermsVersion(), request.getStatus() == AnnualUpfrontRequestStatus.PAYMENT_UNDER_REVIEW,
                request.getDiscardReason() == null ? null : request.getDiscardReason().name(), request.getDecidedAt());
    }

    private static AnnualUpfrontQuoteLine quoteLineOf(InvoiceLine line) {
        return new AnnualUpfrontQuoteLine(line.getLineNumber(), line.getPeriod(), line.getAmount().value(), line.getDiscountAmount().value(),
                line.getAmount().value().minus(line.getDiscountAmount().value()));
    }

    private static AnnualUpfrontRequestDiscountView discountOf(Invoice invoice, List<InvoiceLine> invoiceLines) {
        return invoiceLines.stream().map(InvoiceLine::getDiscount).filter(Objects::nonNull).findFirst()
                .map(discount -> new AnnualUpfrontRequestDiscountView(discount.getConcept(), discount.getCalculation(), invoice.getAmounts().discountsTotal().value())).orElse(null);
    }
}
