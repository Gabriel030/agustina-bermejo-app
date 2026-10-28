package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCalculator;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class AnnualUpfrontDiscountSelector {
    private static final int INVOICE_LEVEL_LINE = 1;

    private final InvoiceDiscountCalculator discountCalculator;

    public AnnualUpfrontDiscountSelector(InvoiceDiscountCalculator discountCalculator) {
        this.discountCalculator = discountCalculator;
    }

    public Map<Integer, InvoiceDiscountCandidate> selectWinningPromotionForEveryLine(List<InvoiceDiscountCandidate> lineCandidates) {
        Map<Long, List<InvoiceDiscountCandidate>> linesByPromotion = lineCandidates.stream().collect(Collectors.groupingBy(InvoiceDiscountCandidate::sourceId));
        List<InvoiceDiscountCandidate> invoiceLevelCandidates = linesByPromotion.values().stream().map(AnnualUpfrontDiscountSelector::invoiceLevelCandidateOf).toList();
        Map<Integer, InvoiceDiscountCandidate> winner = discountCalculator.selectBestDiscountPerInvoiceLine(InvoiceType.ANNUAL_UPFRONT, invoiceLevelCandidates);
        if (winner.isEmpty()) {
            return Map.of();
        }
        Map<Integer, InvoiceDiscountCandidate> winningLines = new TreeMap<>();
        linesByPromotion.get(winner.get(INVOICE_LEVEL_LINE).sourceId()).forEach(candidate -> winningLines.put(candidate.lineNumber(), candidate));
        return winningLines;
    }

    private static InvoiceDiscountCandidate invoiceLevelCandidateOf(List<InvoiceDiscountCandidate> lines) {
        InvoiceDiscountCandidate first = lines.get(0);
        NonNegativeMoney totalSaving = lines.stream().map(InvoiceDiscountCandidate::discountAmount).reduce(NonNegativeMoney.zero(first.baseAmount().value().currency()), NonNegativeMoney::plus);
        return new InvoiceDiscountCandidate(first.discountType(), first.sourceId(), first.studentDiscountId(), first.discountName(), first.applicableInvoiceType(), first.baseAmount(),
                totalSaving, first.installmentNumber(), first.installmentsTotal(), INVOICE_LEVEL_LINE, first.priority(), first.grantedAt(), first.calculation(), first.origin(),
                first.sourceVersionId());
    }
}
