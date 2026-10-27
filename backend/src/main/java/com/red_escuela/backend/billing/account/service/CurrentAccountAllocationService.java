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
import java.util.function.ToIntFunction;
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
        return allocateAccountCredit(lockedAccount, businessDate, actor, Set.of(), Set.of(), Set.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateAccountCreditToOldestInvoicesSkipping(StudentCurrentAccount lockedAccount, LocalDate businessDate,
                                                                                      StatusChangeActor actor, Set<Long> skippedInvoiceIds) {
        return allocateAccountCredit(lockedAccount, businessDate, actor, Set.of(), skippedInvoiceIds, Set.of());
    }

    public List<PlannedInvoiceAllocation> planFullInvoiceAllocationsSkipping(NonNegativeMoney credit, List<OpenInvoiceView> openInvoiceViews,
                                                                             Set<Long> skippedInvoiceIds) {
        return strategy.planFullInvoiceAllocations(credit, openInvoiceViews.stream().filter(view -> !skippedInvoiceIds.contains(view.invoiceId())).toList());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(StudentCurrentAccount lockedAccount, LocalDate businessDate,
                                                                                            StatusChangeActor actor,
                                                                                            Set<Long> programAdmissionInvoiceIdsBeingPaid) {
        return allocateAccountCredit(lockedAccount, businessDate, actor, programAdmissionInvoiceIdsBeingPaid, Set.of(), Set.of());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AccountCreditAllocationResult allocateApprovedTransferCreditSettlingProgramAdmissionInvoicesFirst(StudentCurrentAccount lockedAccount,
                                                                                                            LocalDate businessDate, StatusChangeActor actor,
                                                                                                            Set<Long> programAdmissionInvoiceIdsBeingPaid,
                                                                                                            Set<Long> underReviewInvoiceIdsOfTheApprovedPayment) {
        return allocateAccountCredit(lockedAccount, businessDate, actor, programAdmissionInvoiceIdsBeingPaid, Set.of(), underReviewInvoiceIdsOfTheApprovedPayment);
    }

    private AccountCreditAllocationResult allocateAccountCredit(StudentCurrentAccount lockedAccount, LocalDate businessDate, StatusChangeActor actor,
                                                                Set<Long> programAdmissionInvoiceIdsBeingPaid, Set<Long> skippedInvoiceIds,
                                                                Set<Long> underReviewInvoiceIdsThatMayBePaid) {
        NonNegativeMoney creditToAllocate = accountService.creditAvailableForAllocation(lockedAccount);
        if (creditToAllocate.value().isZero()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        List<Invoice> openInvoices = invoices.listInvoicesAllocatableToAccountCredit(lockedAccount.getId(), underReviewInvoiceIdsThatMayBePaid);
        List<OpenInvoiceView> candidateViews = openInvoices.stream().map(OpenInvoiceView::of)
                .filter(view -> !view.outstandingAmount().value().isZero()).filter(view -> !skippedInvoiceIds.contains(view.invoiceId())).toList();
        Set<Long> coveredProgramAdmissionInvoiceIds = ProgramAdmissionInvoicePaidFirstAllocationStrategy.programAdmissionInvoiceIdsFullyCoveredByCredit(
                strategy, programAdmissionInvoiceIdsBeingPaid, creditToAllocate, candidateViews);
        AccountCreditAllocationStrategy effective = coveredProgramAdmissionInvoiceIds.isEmpty() ? strategy
                : new ProgramAdmissionInvoicePaidFirstAllocationStrategy(strategy, coveredProgramAdmissionInvoiceIds);
        List<OpenInvoiceView> orderedViews = effective.sortInAllocationOrder(candidateViews);
        List<PlannedInvoiceAllocation> plan = effective.planFullInvoiceAllocations(creditToAllocate, orderedViews);
        planPolicies.evaluateRulesUntilFirstViolation(new AllocationPlanContext(lockedAccount.getId(), lockedAccount.getCurrency(), creditToAllocate,
                orderedViews, plan, coveredProgramAdmissionInvoiceIds)).throwIfDenied(PaymentAllocationRejectedException::new);
        if (plan.isEmpty()) {
            return AccountCreditAllocationResult.nothingAllocated();
        }
        Map<Long, Invoice> invoicesById = openInvoices.stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        CreditContributions credits = CreditContributions.availableFrom(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(lockedAccount.getId()),
                allocationRepository.netSourcedByCreditEntry(lockedAccount.getId()), lockedAccount.getUnallocatedCreditAmount()).limitedToOldest(creditToAllocate);
        List<PaymentAllocation> allocations = new ArrayList<>();
        List<Invoice> settledInvoices = new ArrayList<>();
        for (ScheduledAllocation scheduled : AllocationSchedule.scheduleInvoiceAllocations(plan, credits, businessDate)) {
            Invoice invoice = invoicesById.get(scheduled.invoiceId());
            invoice.allocateFullInvoicePayment(scheduled.amount(), businessDate, actor);
            PaymentAllocation allocation = allocationRepository.saveWithSources(
                    PaymentAllocation.recordFullInvoiceAllocation(invoice, scheduled.amount(), scheduled.effectiveDate(), businessDate, actor), scheduled.sources());
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
        return reverseNewestAllocationsUntilCreditCovers(lockedAccount, creditToRemove, businessDate, invoice -> gracePeriodDays, reason, actor);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PaymentAllocation> reverseNewestAllocationsUntilCreditCovers(StudentCurrentAccount lockedAccount, NonNegativeMoney creditToRemove,
                                                                             LocalDate businessDate, ToIntFunction<Long> gracePeriodDaysOfInvoice, String reason,
                                                                             StatusChangeActor actor) {
        List<PaymentAllocation> reversals = new ArrayList<>();
        for (PaymentAllocation current : NewestAllocationsReversalSelection.selectAllocationsToReverse(
                allocationRepository.findCurrentAllocationsByAccountNewestFirst(lockedAccount.getId()), accountService.creditAvailableForAllocation(lockedAccount),
                creditToRemove)) {
            reversals.add(reverseAllocation(lockedAccount, current, businessDate, gracePeriodDaysOfInvoice, reason, actor));
        }
        lockedAccount.requireCreditCovers(accountService.creditAvailableForAllocation(lockedAccount), creditToRemove);
        return reversals;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> listInvoiceIdsOfCurrentAllocations(StudentCurrentAccount lockedAccount) {
        return allocationRepository.findCurrentAllocationsByAccountNewestFirst(lockedAccount.getId()).stream().map(PaymentAllocation::getInvoiceId).distinct().toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PaymentAllocation> reverseAllocationsFundedByPayment(StudentCurrentAccount lockedAccount, Long paymentId, LocalDate businessDate,
                                                                    int gracePeriodDays, String reason, StatusChangeActor actor) {
        return reverseAllocationsFundedByPayment(lockedAccount, paymentId, businessDate, invoice -> gracePeriodDays, reason, actor);
    }

    @Transactional(readOnly = true)
    public List<Long> listInvoiceIdsFundedByPayment(Long studentCurrentAccountId, Long paymentId) {
        return allocationRepository.findCurrentAllocationsFundedByPaymentNewestFirst(studentCurrentAccountId, paymentId).stream().map(PaymentAllocation::getInvoiceId)
                .distinct().sorted().toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> listInvoiceIdsOfAllocationsFundedByPayment(StudentCurrentAccount lockedAccount, Long paymentId) {
        return allocationRepository.findCurrentAllocationsFundedByPaymentNewestFirst(lockedAccount.getId(), paymentId).stream().map(PaymentAllocation::getInvoiceId).distinct().toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PaymentAllocation> reverseAllocationsFundedByPayment(StudentCurrentAccount lockedAccount, Long paymentId, LocalDate businessDate,
                                                                    ToIntFunction<Long> gracePeriodDaysOfInvoice, String reason, StatusChangeActor actor) {
        List<PaymentAllocation> toReverse = allocationRepository.findCurrentAllocationsFundedByPaymentNewestFirst(lockedAccount.getId(), paymentId);
        List<PaymentAllocation> reversals = new ArrayList<>();
        for (PaymentAllocation current : toReverse) {
            reversals.add(reverseAllocation(lockedAccount, current, businessDate, gracePeriodDaysOfInvoice, reason, actor));
        }
        return reversals;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentAllocation reverseAllocation(StudentCurrentAccount lockedAccount, PaymentAllocation allocation, LocalDate businessDate,
                                               int gracePeriodDays, String reason, StatusChangeActor actor) {
        return reverseAllocation(lockedAccount, allocation, businessDate, invoice -> gracePeriodDays, reason, actor);
    }

    private PaymentAllocation reverseAllocation(StudentCurrentAccount lockedAccount, PaymentAllocation allocation, LocalDate businessDate,
                                                ToIntFunction<Long> gracePeriodDaysOfInvoice, String reason, StatusChangeActor actor) {
        Invoice invoice = invoices.lockInvoice(allocation.getInvoiceId());
        reversalPolicies.evaluateRulesUntilFirstViolation(new AllocationReversalContext(allocation.getId(),
                allocation.isReversal() || allocationRepository.existsByReversesAllocationId(allocation.getId()),
                lockedAccount.getId(), allocation.getStudentCurrentAccountId()))
                .throwIfDenied(PaymentAllocationRejectedException::new);
        PaymentAllocation reversal = allocationRepository.saveReversalWithSourcesOfTheReversedAllocation(allocation.recordAllocationReversal(businessDate, reason, actor));
        invoice.reversePaymentAllocation(businessDate, gracePeriodDaysOfInvoice.applyAsInt(allocation.getInvoiceId()), actor, reason);
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
