package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.PaymentAllocation;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
class PaymentCreditReversal {
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final ReversedInvoiceGracePeriods gracePeriods;
    private final InvoiceAllocationQueryService invoiceQueries;

    PaymentCreditReversal(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService, ReversedInvoiceGracePeriods gracePeriods,
                          InvoiceAllocationQueryService invoiceQueries) {
        this.invoiceQueries = invoiceQueries;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.gracePeriods = gracePeriods;
    }

    ReversedPaymentAllocations reverseAllocationsOf(StudentCurrentAccount lockedAccount, Payment payment, LocalDate businessDate, String concept, StatusChangeActor actor) {
        CurrentAccountEntry credit = accountService.findPaymentCreditNotYetReversed(payment.getId())
                .orElseThrow(() -> new IllegalStateException("The payment " + payment.getId() + " has no credit in the current account to reverse"));
        invoiceQueries.lockOpenInvoicesOfAccountAndTheseInIdOrder(lockedAccount.getId(), allocationService.listInvoiceIdsOfAllocationsFundedByPayment(lockedAccount, payment.getId()));
        List<PaymentAllocation> reversals = allocationService.reverseAllocationsFundedByPayment(lockedAccount, payment.getId(), businessDate,
                gracePeriods::gracePeriodDaysOf, concept, actor);
        return new ReversedPaymentAllocations(credit, reversals.stream().map(PaymentAllocation::getInvoiceId).distinct().toList());
    }

    record ReversedPaymentAllocations(CurrentAccountEntry credit, List<Long> invoicesReopened) {
    }
}
