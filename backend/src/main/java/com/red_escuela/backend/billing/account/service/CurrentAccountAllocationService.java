package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.ProgramAdmissionInvoicePaidFirstAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.AllocationSchedule;
import com.red_escuela.backend.billing.account.domain.CreditContributions;
import com.red_escuela.backend.billing.account.domain.NewestAllocationsReversalSelection;
import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.billing.account.domain.ScheduledAllocation;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.exception.PaymentAllocationRejectedException;
import com.red_escuela.backend.billing.account.policy.AllocationPlanContext;
import com.red_escuela.backend.billing.account.policy.AllocationReversalContext;
import com.red_escuela.backend.billing.account.repository.CurrentAccountEntryRepository;
import com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.events.PaymentAllocationReversedEvent;
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
import java.util.Set;
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
        return allocateAccountCredit(lockedAccount, businessDate, actor, strategy, Set.of(), Set.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateAccountCreditToOldestInvoicesSkipping(StudentCurrentAccount lockedAccount, LocalDate businessDate,
                                                                                      StatusChangeActor actor, Set<Long> skippedInvoiceIds) {
        return allocateAccountCredit(lockedAccount, businessDate, actor, strategy, Set.of(), skippedInvoiceIds);
    }

    public List<PlannedInvoiceAllocation> planFullInvoiceAllocationsSkipping(NonNegativeMoney credit, List<OpenInvoiceView> openInvoiceViews,
                                                                             Set<Long> skippedInvoiceIds) {
        return strategy.planFullInvoiceAllocations(credit, openInvoiceViews.stream().filter(view -> !skippedInvoiceIds.contains(view.invoiceId())).toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(StudentCurrentAccount lockedAccount, LocalDate businessDate,
                                                                                            StatusChangeActor actor,
                                                                                            Set<Long> programAdmissionInvoiceIdsBeingPaid) {
        AccountCreditAllocationStrategy effective = programAdmissionInvoiceIdsBeingPaid.isEmpty() ? strategy
                : new ProgramAdmissionInvoicePaidFirstAllocationStrategy(strategy, programAdmissionInvoiceIdsBeingPaid);
        return allocateAccountCredit(lockedAccount, businessDate, actor, effective, programAdmissionInvoiceIdsBeingPaid, Set.of());
    }

    private AccountCreditAllocationResult allocateAccountCredit(StudentCurrentAccount lockedAccount, LocalDate businessDate, StatusChangeActor actor,
                                                                AccountCreditAllocationStrategy strategy,
                                                                Set<Long> programAdmissionInvoiceIdsBeingPaid, Set<Long> skippedInvoiceIds) {
        NonNegativeMoney creditToAllocate = lockedAccount.getUnallocatedCreditAmount();
        if (creditToAllocate.value().isZero()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        List<Invoice> openInvoices = invoices.listOpenInvoicesOfAccount(lockedAccount.getId());
        List<OpenInvoiceView> orderedViews = strategy.sortInAllocationOrder(openInvoices.stream().map(OpenInvoiceView::of)
                .filter(view -> !view.outstandingAmount().value().isZero()).filter(view -> !skippedInvoiceIds.contains(view.invoiceId())).toList());
        List<PlannedInvoiceAllocation> plan = strategy.planFullInvoiceAllocations(creditToAllocate, orderedViews);
        planPolicies.evaluateRulesUntilFirstViolation(new AllocationPlanContext(lockedAccount.getId(), lockedAccount.getCurrency(), creditToAllocate,
                orderedViews, plan, programAdmissionInvoiceIdsBeingPaid)).throwIfDenied(PaymentAllocationRejectedException::new);
        if (plan.isEmpty()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        Map<Long, Invoice> invoicesById = openInvoices.stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        CreditContributions credits = CreditContributions.fromEntriesOldestFirst(
                entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(lockedAccount.getId()));
        List<PaymentAllocation> allocations = new ArrayList<>();
        List<Invoice> settledInvoices = new ArrayList<>();
        for (ScheduledAllocation scheduled : AllocationSchedule.scheduleInvoiceAllocations(plan, credits, creditToAllocate, businessDate)) {
            Invoice invoice = invoicesById.get(scheduled.invoiceId());
            invoice.allocateFullInvoicePayment(scheduled.amount(), businessDate, actor);
            PaymentAllocation allocation = allocationRepository.save(
                    PaymentAllocation.recordFullInvoiceAllocation(invoice, scheduled.amount(), scheduled.effectiveDate(), businessDate, actor));
            accountService.consumeUnallocatedCreditForAllocation(lockedAccount, scheduled.amount());
            allocations.add(allocation);
            settledInvoices.add(invoice);
            log.info("Imputación registrada: cuenta={}, factura={}, monto={}, fechaEfectiva={}, creditoSinImputarRestante={}",
                    lockedAccount.getId(), invoice.getId(), scheduled.amount().value().amount(), scheduled.effectiveDate(),
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
        for (PaymentAllocation current : NewestAllocationsReversalSelection.selectAllocationsToReverse(
                allocationRepository.findCurrentAllocationsByAccountNewestFirst(lockedAccount.getId()), lockedAccount.getUnallocatedCreditAmount(),
                creditToRemove)) {
            reversals.add(reverseAllocation(lockedAccount, current, businessDate, gracePeriodDays, reason, actor));
        }
        lockedAccount.requireUnallocatedCreditCovers(creditToRemove);
        return reversals;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentAllocation reverseAllocation(StudentCurrentAccount lockedAccount, PaymentAllocation allocation, LocalDate businessDate,
                                               int gracePeriodDays, String reason, StatusChangeActor actor) {
        Invoice invoice = invoices.lockInvoice(allocation.getInvoiceId());
        reversalPolicies.evaluateRulesUntilFirstViolation(new AllocationReversalContext(allocation.getId(),
                allocation.isReversal() || allocationRepository.existsByReversesAllocationId(allocation.getId()),
                lockedAccount.getId(), allocation.getStudentCurrentAccountId()))
                .throwIfDenied(PaymentAllocationRejectedException::new);
        PaymentAllocation reversal = allocationRepository.save(allocation.recordAllocationReversal(businessDate, reason, actor));
        invoice.reversePaymentAllocation(businessDate, gracePeriodDays, actor, reason);
        eventPublisher.publishEvent(PaymentAllocationReversedEvent.reversedFor(allocation.getInvoiceId(), lockedAccount.getId(), businessDate));
        accountService.restoreUnallocatedCreditFromReversedAllocation(lockedAccount, allocation.getAmount());
        log.info("Imputación revertida: cuenta={}, factura={}, monto={}, motivo={}", lockedAccount.getId(), allocation.getInvoiceId(),
                allocation.getAmount().value().amount(), reason);
        return reversal;
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
