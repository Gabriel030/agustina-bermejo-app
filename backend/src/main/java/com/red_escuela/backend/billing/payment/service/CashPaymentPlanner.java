package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.invoice.service.OnlineOnlyInvoiceResolver;
import com.red_escuela.backend.billing.payment.domain.CashPaymentFingerprint;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CashPaymentPlanner {

    private final InvoiceAllocationQueryService invoiceQueries;
    private final OnlineOnlyInvoiceResolver onlineOnlyInvoices;
    private final CurrentAccountAllocationService allocationService;

    public CashPaymentPlanner(InvoiceAllocationQueryService invoiceQueries, OnlineOnlyInvoiceResolver onlineOnlyInvoices,
                              CurrentAccountAllocationService allocationService) {
        this.invoiceQueries = invoiceQueries;
        this.onlineOnlyInvoices = onlineOnlyInvoices;
        this.allocationService = allocationService;
    }

    public record CashPaymentCandidates(List<Invoice> openInvoices, Set<Long> skippedInvoiceIds) {

        public CashPaymentCandidates {
            openInvoices = List.copyOf(openInvoices);
            skippedInvoiceIds = Set.copyOf(skippedInvoiceIds);
        }

        public static CashPaymentCandidates none() {
            return new CashPaymentCandidates(List.of(), Set.of());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashPaymentCandidates findCandidatesOfAccount(Long studentCurrentAccountId) {
        List<Invoice> openInvoices = invoiceQueries.listOpenInvoicesOfAccount(studentCurrentAccountId);
        return new CashPaymentCandidates(openInvoices, onlineOnlyInvoices.findInvoiceIdsOfOnlineOnlyOfferings(openInvoices));
    }

    public CashPaymentPreview previewCashPayment(Long studentId, Currency currency, CashPaymentCandidates candidates, NonNegativeMoney unallocatedBefore,
                                                 NonNegativeMoney amount) {
        NonNegativeMoney available = unallocatedBefore.plus(amount);
        List<PlannedInvoiceAllocation> plan = allocationService.planFullInvoiceAllocationsSkipping(available,
                candidates.openInvoices().stream().map(OpenInvoiceView::of).toList(), candidates.skippedInvoiceIds());
        Map<Long, Invoice> invoicesById = candidates.openInvoices().stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        List<CashPaymentPreview.CoveredInvoice> covered = plan.stream()
                .map(planned -> new CashPaymentPreview.CoveredInvoice(planned.invoiceId(), invoicesById.get(planned.invoiceId()).getPrimaryLine().getConcept(),
                        invoicesById.get(planned.invoiceId()).getDueDate(), planned.amount()))
                .toList();
        Money coveredTotal = covered.stream().map(invoice -> invoice.amount().value()).reduce(Money.zero(currency), Money::plus);
        NonNegativeMoney unallocatedAfterwards = NonNegativeMoney.of(available.value().minus(coveredTotal));
        return new CashPaymentPreview(covered, unallocatedAfterwards, CashPaymentFingerprint.of(studentId, currency, covered, unallocatedAfterwards));
    }
}
