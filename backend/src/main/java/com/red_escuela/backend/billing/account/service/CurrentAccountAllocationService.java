package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.AllocationEffectiveDateCalculator;
import com.red_escuela.backend.billing.account.domain.CreditContribution;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntryType;
import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.exception.PaymentAllocationRejectedException;
import com.red_escuela.backend.billing.account.policy.AllocationPlanContext;
import com.red_escuela.backend.billing.account.policy.AllocationReversalContext;
import com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CurrentAccountAllocationService {

    private static final Logger log = LoggerFactory.getLogger(CurrentAccountAllocationService.class);

    private final AccountCreditAllocationStrategy strategy;
    private final PolicySet<AllocationPlanContext> planPolicies;
    private final PolicySet<AllocationReversalContext> reversalPolicies;
    private final InvoiceAllocationQueryService invoices;
    private final PaymentAllocationRepository allocationRepository;
    private final CurrentAccountEntryRepository entryRepository;
    private final StudentCurrentAccountService accountService;
    private final ApplicationEventPublisher eventPublisher;

    public CurrentAccountAllocationService(AccountCreditAllocationStrategy strategy, PolicySet<AllocationPlanContext> planPolicies,
                                           PolicySet<AllocationReversalContext> reversalPolicies, InvoiceAllocationQueryService invoices,
                                           PaymentAllocationRepository allocationRepository, CurrentAccountEntryRepository entryRepository,
                                           StudentCurrentAccountService accountService, ApplicationEventPublisher eventPublisher) {
        this.strategy = strategy;
        this.planPolicies = planPolicies;
        this.reversalPolicies = reversalPolicies;
        this.invoices = invoices;
        this.allocationRepository = allocationRepository;
        this.entryRepository = entryRepository;
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateAccountCreditToOldestInvoices(StudentCurrentAccount lockedAccount, LocalDate businessDate,
                                                                              StatusChangeActor actor) {
        NonNegativeMoney creditToAllocate = lockedAccount.getUnallocatedCreditAmount();
        if (creditToAllocate.value().isZero()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        List<Invoice> openInvoices = invoices.listOpenInvoicesOfAccount(lockedAccount.getId());
        List<OpenInvoiceView> orderedViews = strategy.sortInAllocationOrder(openInvoices.stream().map(OpenInvoiceView::of).toList());
        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(creditToAllocate, orderedViews);
        planPolicies.evaluateRulesUntilFirstViolation(new AllocationPlanContext(lockedAccount.getId(), lockedAccount.getCurrency(), creditToAllocate,
                orderedViews, plan)).throwIfDenied(PaymentAllocationRejectedException::new);
        if (plan.isEmpty()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        Map<Long, Invoice> invoicesById = openInvoices.stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        List<CreditContribution> credits = listCreditContributionsOldestFirst(lockedAccount.getId());
        Money creditedInTotal = credits.stream().map(credit -> credit.amount().value()).reduce(Money.zero(lockedAccount.getCurrency()), Money::plus);
        Money alreadyConsumed = creditedInTotal.minus(creditToAllocate.value());
        NonNegativeMoney consumedCredit = alreadyConsumed.isNegative() ? NonNegativeMoney.zero(lockedAccount.getCurrency()) : NonNegativeMoney.of(alreadyConsumed);
        List<PaymentAllocation> allocations = new ArrayList<>();
        List<Invoice> settledInvoices = new ArrayList<>();
        for (PlannedInvoiceAllocation planned : plan) {
            Invoice invoice = invoicesById.get(planned.invoiceId());
            LocalDate effectiveDate = AllocationEffectiveDateCalculator.calculateEffectiveDate(credits, consumedCredit, planned.amount(), businessDate);
            invoice.allocateFullInvoicePayment(planned.amount(), businessDate, actor);
            PaymentAllocation allocation = allocationRepository.save(
                    PaymentAllocation.recordFullInvoiceAllocation(invoice, planned.amount(), effectiveDate, businessDate, actor));
            accountService.consumeUnallocatedCreditForAllocation(lockedAccount, planned.amount());
            consumedCredit = consumedCredit.plus(planned.amount());
            allocations.add(allocation);
            settledInvoices.add(invoice);
            log.info("Imputación registrada: cuenta={}, factura={}, monto={}, fechaEfectiva={}, creditoSinImputarRestante={}",
                    lockedAccount.getId(), invoice.getId(), planned.amount().value().amount(), effectiveDate,
                    lockedAccount.getUnallocatedCreditAmount().value().amount());
        }
        publishAllocationEvents(lockedAccount, plan, settledInvoices, businessDate);
        return new AccountCreditAllocationResult(allocations, settledInvoices);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PaymentAllocation> reverseNewestAllocationsUntilCreditCovers(StudentCurrentAccount lockedAccount, NonNegativeMoney creditToRemove,
                                                                             LocalDate businessDate, int gracePeriodDays, String reason,
                                                                             StatusChangeActor actor) {
        List<PaymentAllocation> reversals = new ArrayList<>();
        for (PaymentAllocation current : allocationRepository.findCurrentAllocationsByAccountNewestFirst(lockedAccount.getId())) {
            if (!creditToRemove.value().isGreaterThan(lockedAccount.getUnallocatedCreditAmount().value())) {
                break;
            }
            reversals.add(reverseAllocation(lockedAccount, current, businessDate, gracePeriodDays, reason, actor));
        }
        lockedAccount.requireUnallocatedCreditCovers(creditToRemove);
        return reversals;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentAllocation reverseAllocation(StudentCurrentAccount lockedAccount, PaymentAllocation allocation, LocalDate businessDate,
                                               int gracePeriodDays, String reason, StatusChangeActor actor) {
        reversalPolicies.evaluateRulesUntilFirstViolation(new AllocationReversalContext(allocation.getId(),
                allocation.isReversal() || allocationRepository.existsByReversesAllocationId(allocation.getId())))
                .throwIfDenied(PaymentAllocationRejectedException::new);
        PaymentAllocation reversal = allocationRepository.save(allocation.recordAllocationReversal(businessDate, reason, actor));
        Invoice invoice = invoices.lockInvoice(allocation.getInvoiceId());
        invoice.reversePaymentAllocation(businessDate, gracePeriodDays, actor, reason);
        accountService.restoreUnallocatedCreditFromReversedAllocation(lockedAccount, allocation.getAmount());
        log.info("Imputación revertida: cuenta={}, factura={}, monto={}, motivo={}", lockedAccount.getId(), allocation.getInvoiceId(),
                allocation.getAmount().value().amount(), reason);
        return reversal;
    }

    private List<CreditContribution> listCreditContributionsOldestFirst(Long accountId) {
        return entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId).stream()
                .filter(this::addsAllocatableCredit)
                .map(entry -> new CreditContribution(entry.getEffectiveDate(), entry.getAmount()))
                .toList();
    }

    private boolean addsAllocatableCredit(CurrentAccountEntry entry) {
        return entry.getEntryType() == CurrentAccountEntryType.PAYMENT_RECEIVED || entry.getEntryType() == CurrentAccountEntryType.CREDIT_NOTE_ISSUED;
    }

    private void publishAllocationEvents(StudentCurrentAccount account, List<PlannedInvoiceAllocation> plan, List<Invoice> settledInvoices,
                                         LocalDate businessDate) {
        Money total = plan.stream().map(planned -> planned.amount().value()).reduce(Money.zero(account.getCurrency()), Money::plus);
        settledInvoices.forEach(invoice -> eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(invoice.getId(), account.getId(),
                InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name(), businessDate)));
        eventPublisher.publishEvent(AccountCreditAllocatedEvent.allocatedFor(account.getId(), plan.stream().map(PlannedInvoiceAllocation::invoiceId).toList(),
                NonNegativeMoney.of(total), businessDate));
    }
}
