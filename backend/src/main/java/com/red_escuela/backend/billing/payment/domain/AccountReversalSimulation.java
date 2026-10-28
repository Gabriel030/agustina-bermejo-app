package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.NewestAllocationsReversalSelection;
import com.red_escuela.backend.billing.account.domain.OpenInvoiceView;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.PlannedInvoiceAllocation;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.InvoiceImpact;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects.LateFeeReinstatementImpact;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class AccountReversalSimulation {
    private static final long FIRST_SIMULATED_DEBIT_NOTE_ID = Long.MAX_VALUE / 2;

    private final AccountCreditAllocationStrategy strategy;
    private final Long studentCurrentAccountId;
    private final NonNegativeMoney reservedCredit;
    private final Map<Long, SimulatedInvoice> invoicesById = new LinkedHashMap<>();
    private final List<PaymentAllocation> currentAllocationsNewestFirst;
    private final Map<Long, InvoiceImpact> reopenedByInvoice = new LinkedHashMap<>();
    private final List<InvoiceImpact> settled = new ArrayList<>();
    private final List<LateFeeReinstatementImpact> lateFees = new ArrayList<>();
    private final Map<Long, Integer> lateFeePositionBySimulatedId = new LinkedHashMap<>();
    private NonNegativeMoney unallocatedCredit;
    private long nextSimulatedDebitNoteId = FIRST_SIMULATED_DEBIT_NOTE_ID;

    private AccountReversalSimulation(AccountCreditAllocationStrategy strategy, Long studentCurrentAccountId, NonNegativeMoney unallocatedCredit, NonNegativeMoney reservedCredit,
                                      List<PaymentAllocation> currentAllocationsNewestFirst) {
        this.strategy = strategy;
        this.studentCurrentAccountId = studentCurrentAccountId;
        this.unallocatedCredit = unallocatedCredit;
        this.reservedCredit = reservedCredit;
        this.currentAllocationsNewestFirst = new ArrayList<>(currentAllocationsNewestFirst);
    }

    public static AccountReversalSimulation of(AccountCreditAllocationStrategy strategy, Long studentCurrentAccountId, NonNegativeMoney unallocatedCredit,
                                               NonNegativeMoney reservedCredit, List<PaymentAllocation> currentAllocationsNewestFirst, Collection<Invoice> knownInvoices,
                                               Collection<Long> invoiceIdsAwaitingAuthorization) {
        AccountReversalSimulation simulation = new AccountReversalSimulation(strategy, studentCurrentAccountId, unallocatedCredit, reservedCredit, currentAllocationsNewestFirst);
        knownInvoices.forEach(invoice -> simulation.invoicesById.put(invoice.getId(), SimulatedInvoice.copyOf(invoice, !invoiceIdsAwaitingAuthorization.contains(invoice.getId()))));
        return simulation;
    }

    public boolean hasReservedCredit() {
        return !reservedCredit.value().isZero();
    }

    public List<PaymentAllocation> currentAllocationsNewestFirst() {
        return List.copyOf(currentAllocationsNewestFirst);
    }

    public void reverseAllocations(List<PaymentAllocation> allocations, LocalDate businessDate, ToIntFunction<Long> gracePeriodDaysOfInvoice) {
        for (PaymentAllocation allocation : allocations) {
            SimulatedInvoice invoice = invoicesById.get(allocation.getInvoiceId());
            InvoicePaymentStatus statusBefore = invoice.status();
            NonNegativeMoney outstandingBefore = invoice.outstanding();
            Invoice source = invoice.source();
            invoice.reopen(source.getPaymentStatusIfAllocationReversedOn(businessDate, gracePeriodDaysOfInvoice.applyAsInt(allocation.getInvoiceId())),
                    source.getOutstandingAmountIfAllocationReversed());
            NonNegativeMoney alreadyReversed = reopenedByInvoice.containsKey(allocation.getInvoiceId())
                    ? reopenedByInvoice.get(allocation.getInvoiceId()).creditReversed() : NonNegativeMoney.zero(allocation.getAmount().value().currency());
            reopenedByInvoice.put(allocation.getInvoiceId(), new InvoiceImpact(allocation.getInvoiceId(), statusBefore, invoice.status(), outstandingBefore,
                    invoice.outstanding(), alreadyReversed.plus(allocation.getAmount())));
            currentAllocationsNewestFirst.removeIf(current -> current.getId().equals(allocation.getId()));
            unallocatedCredit = unallocatedCredit.plus(allocation.getAmount());
        }
    }

    public void reverseNewestAllocationsUntilCreditCovers(NonNegativeMoney creditToRemove, LocalDate businessDate, ToIntFunction<Long> gracePeriodDaysOfInvoice) {
        reverseAllocations(NewestAllocationsReversalSelection.selectAllocationsToReverse(currentAllocationsNewestFirst, creditAvailableForAllocation(), creditToRemove),
                businessDate, gracePeriodDaysOfInvoice);
        if (creditToRemove.value().isGreaterThan(creditAvailableForAllocation().value())) {
            throw new InsufficientAccountCreditException();
        }
    }

    public void removePaymentCredit(NonNegativeMoney amount) {
        if (amount.value().isGreaterThan(unallocatedCredit.value())) {
            throw new InsufficientAccountCreditException();
        }
        unallocatedCredit = unallocatedCredit.minusNotBelowZero(amount);
    }

    public void addPaymentCredit(NonNegativeMoney amount) {
        unallocatedCredit = unallocatedCredit.plus(amount);
    }

    public List<Long> reopenedInvoiceIdsInAscendingOrder() {
        return reopenedByInvoice.keySet().stream().sorted().toList();
    }

    public InvoicePaymentStatus currentStatusOf(Long invoiceId) {
        return invoicesById.get(invoiceId).status();
    }

    public void issueLateFeeDebitNote(Long originInvoiceId, LocalDate firstDay, LocalDate lastDay, int chargedDays, NonNegativeMoney amount, LocalDate issueDate,
                                      LocalDate debitNoteDueDate) {
        Long simulatedId = nextSimulatedDebitNoteId++;
        invoicesById.put(simulatedId, SimulatedInvoice.lateFeeDebitNote(simulatedId, originInvoiceId, studentCurrentAccountId, debitNoteDueDate, issueDate, amount));
        lateFeePositionBySimulatedId.put(simulatedId, lateFees.size());
        lateFees.add(new LateFeeReinstatementImpact(originInvoiceId, firstDay, lastDay, chargedDays, amount, false));
        allocateAccountCredit();
    }

    public void allocateAccountCredit() {
        NonNegativeMoney credit = creditAvailableForAllocation();
        if (credit.value().isZero()) {
            return;
        }
        List<OpenInvoiceView> candidates = invoicesById.values().stream().filter(SimulatedInvoice::isOpenForAllocation).map(SimulatedInvoice::asOpenInvoiceView).toList();
        for (PlannedInvoiceAllocation planned : strategy.planFullInvoiceAllocations(credit, strategy.sortInAllocationOrder(candidates))) {
            SimulatedInvoice invoice = invoicesById.get(planned.invoiceId());
            InvoicePaymentStatus statusBefore = invoice.status();
            NonNegativeMoney outstandingBefore = invoice.outstanding();
            invoice.settle();
            unallocatedCredit = unallocatedCredit.minusNotBelowZero(planned.amount());
            if (invoice.persisted()) {
                settled.add(new InvoiceImpact(invoice.invoiceId(), statusBefore, InvoicePaymentStatus.PAID, outstandingBefore, invoice.outstanding(),
                        NonNegativeMoney.zero(outstandingBefore.value().currency())));
            } else {
                int position = lateFeePositionBySimulatedId.get(invoice.invoiceId());
                LateFeeReinstatementImpact lateFee = lateFees.get(position);
                lateFees.set(position, new LateFeeReinstatementImpact(lateFee.originInvoiceId(), lateFee.firstDay(), lateFee.lastDay(), lateFee.chargedDays(), lateFee.amount(),
                        true));
            }
        }
    }

    public PaymentReversalEffects effects() {
        return PaymentReversalEffects.calculated(List.copyOf(reopenedByInvoice.values()), settled, lateFees);
    }

    private NonNegativeMoney creditAvailableForAllocation() {
        return unallocatedCredit.minusNotBelowZero(reservedCredit);
    }
}
