package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationStrategy;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.AnnulledLateFeeReinstatementPlanning;
import com.red_escuela.backend.billing.payment.domain.AccountReversalSimulation;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.billing.payment.operation.ReversedInvoiceGracePeriods;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class PaymentReversalEffectsPlanner {
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final AccountCreditAllocationStrategy allocationStrategy;
    private final ReversedInvoiceGracePeriods gracePeriods;
    private final AnnulledLateFeeReinstatementPlanning lateFeePlanning;

    PaymentReversalEffectsPlanner(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService, InvoiceAllocationQueryService invoiceQueries,
                                  AccountCreditAllocationStrategy allocationStrategy, ReversedInvoiceGracePeriods gracePeriods,
                                  AnnulledLateFeeReinstatementPlanning lateFeePlanning) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.allocationStrategy = allocationStrategy;
        this.gracePeriods = gracePeriods;
        this.lateFeePlanning = lateFeePlanning;
    }

    PaymentReversalEffects planPaymentCreditReversal(Payment payment, NonNegativeMoney credit, LocalDate today) {
        StudentCurrentAccount account = accountService.findStudentCurrentAccountById(payment.getStudentCurrentAccountId()).orElseThrow();
        NonNegativeMoney reserved = accountService.reservedCreditOf(account);
        List<PaymentAllocation> funded = allocationService.listCurrentAllocationsFundedByPaymentNewestFirst(account.getId(), payment.getId());
        boolean hasReservedCredit = !reserved.value().isZero();
        List<PaymentAllocation> current = hasReservedCredit ? allocationService.listCurrentAllocationsNewestFirst(account.getId()) : funded;
        AccountReversalSimulation simulation = simulationOf(account, reserved, current, current);
        simulation.reverseAllocations(funded, today, gracePeriods::gracePeriodDaysOf);
        if (hasReservedCredit) {
            simulation.reverseNewestAllocationsUntilCreditCovers(credit, today, gracePeriods::gracePeriodDaysOf);
        }
        simulation.removePaymentCredit(credit);
        for (Long originInvoiceId : simulation.reopenedInvoiceIdsInAscendingOrder()) {
            boolean originIsOverdueWhenLoaded = simulation.currentStatusOf(originInvoiceId) == InvoicePaymentStatus.OVERDUE;
            for (Long correctionId : lateFeePlanning.findCorrectionIdsAwaitingReinstatement(originInvoiceId)) {
                lateFeePlanning.planReinstatement(originInvoiceId, correctionId, originIsOverdueWhenLoaded, today).ifPresent(
                        planned -> simulation.issueLateFeeDebitNote(originInvoiceId, planned.firstDay(), planned.lastDay(), planned.chargedDays(), planned.amount(),
                                planned.issueDate(), planned.debitNoteDueDate()));
            }
        }
        simulation.allocateAccountCredit();
        return simulation.effects();
    }

    PaymentReversalEffects planChargebackReversal(Payment payment, Chargeback chargeback) {
        StudentCurrentAccount account = accountService.findStudentCurrentAccountById(payment.getStudentCurrentAccountId()).orElseThrow();
        AccountReversalSimulation simulation = simulationOf(account, accountService.reservedCreditOf(account), List.of(), List.of());
        simulation.addPaymentCredit(chargeback.getAmount());
        simulation.allocateAccountCredit();
        return simulation.effects();
    }

    private AccountReversalSimulation simulationOf(StudentCurrentAccount account, NonNegativeMoney reserved, List<PaymentAllocation> current,
                                                   List<PaymentAllocation> allocationsWhoseInvoicesMayReopen) {
        Set<Long> reopenableInvoiceIds = new LinkedHashSet<>();
        allocationsWhoseInvoicesMayReopen.forEach(allocation -> reopenableInvoiceIds.add(allocation.getInvoiceId()));
        Map<Long, Invoice> known = new LinkedHashMap<>();
        invoiceQueries.listOpenInvoicesOfAccountForAllocationPreview(account.getId()).forEach(invoice -> known.put(invoice.getId(), invoice));
        if (!reopenableInvoiceIds.isEmpty()) {
            invoiceQueries.findInvoicesForPreview(reopenableInvoiceIds).forEach(invoice -> known.putIfAbsent(invoice.getId(), invoice));
        }
        return AccountReversalSimulation.of(allocationStrategy, account.getId(), account.getUnallocatedCreditAmount(), reserved, new ArrayList<>(current), known.values(),
                invoiceQueries.findInvoiceIdsAwaitingAuthorization(reopenableInvoiceIds));
    }
}
