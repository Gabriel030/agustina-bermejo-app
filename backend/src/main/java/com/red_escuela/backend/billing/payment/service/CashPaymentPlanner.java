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
    public void lockOpenInvoicesOfLockedAccount(Long studentCurrentAccountId) {
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(studentCurrentAccountId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CashPaymentCandidates findCandidatesOfAccount(Long studentCurrentAccountId) {
        List<Invoice> openInvoices = invoiceQueries.listInvoicesAllocatableToAccountCredit(studentCurrentAccountId, Set.of());
        return new CashPaymentCandidates(openInvoices, onlineOnlyInvoices.findInvoiceIdsOfOnlineOnlyOfferings(openInvoices));
    }

    public CashPaymentPreview previewCashPayment(Long studentId, Currency currency, CashPaymentCandidates candidates, NonNegativeMoney unallocatedBefore,
                                                 NonNegativeMoney amount) {
        NonNegativeMoney available = unallocatedBefore.plus(amount);
        List<OpenInvoiceView> views = candidates.openInvoices().stream().map(OpenInvoiceView::of).toList();
        Map<Long, Invoice> invoicesById = candidates.openInvoices().stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        List<PlannedInvoiceAllocation> cashPass = allocationService.planFullInvoiceAllocationsSkipping(available, views, candidates.skippedInvoiceIds());
        Money afterCashPass = available.value().minus(totalOf(cashPass, currency));
        Set<Long> paidInCashPass = cashPass.stream().map(PlannedInvoiceAllocation::invoiceId).collect(Collectors.toSet());
        List<PlannedInvoiceAllocation> remainingCreditPass = allocationService.planFullInvoiceAllocationsSkipping(NonNegativeMoney.of(afterCashPass), views,
                paidInCashPass);
        List<CashPaymentPreview.CoveredInvoice> coveredByCash = coveredInvoicesOf(cashPass, invoicesById);
        List<CashPaymentPreview.CoveredInvoice> coveredByRemainingCredit = coveredInvoicesOf(remainingCreditPass, invoicesById);
        NonNegativeMoney unallocatedAfterwards = NonNegativeMoney.of(afterCashPass.minus(totalOf(remainingCreditPass, currency)));
        return new CashPaymentPreview(coveredByCash, coveredByRemainingCredit, unallocatedAfterwards,
                CashPaymentFingerprint.of(studentId, currency, amount, coveredByCash, coveredByRemainingCredit, unallocatedAfterwards));
    }

    private static Money totalOf(List<PlannedInvoiceAllocation> plan, Currency currency) {
        return plan.stream().map(planned -> planned.amount().value()).reduce(Money.zero(currency), Money::plus);
    }

    private static List<CashPaymentPreview.CoveredInvoice> coveredInvoicesOf(List<PlannedInvoiceAllocation> plan, Map<Long, Invoice> invoicesById) {
        return plan.stream()
                .map(planned -> new CashPaymentPreview.CoveredInvoice(planned.invoiceId(), invoicesById.get(planned.invoiceId()).getPrimaryLine().getConcept(),
                        invoicesById.get(planned.invoiceId()).getDueDate(), planned.amount()))
                .toList();
    }
}
