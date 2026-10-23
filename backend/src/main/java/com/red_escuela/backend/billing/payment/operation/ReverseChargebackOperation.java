package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.ChargebackReversedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.exception.ChargebackNotFoundException;
import com.red_escuela.backend.billing.payment.exception.PaymentNotFoundException;
import com.red_escuela.backend.billing.payment.repository.ChargebackRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class ReverseChargebackOperation implements BillingOperation<ReverseChargebackCommand, PaymentReversalResult> {
    private static final Logger log = LoggerFactory.getLogger(ReverseChargebackOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final ChargebackRepository chargebacks;
    private final Clock clock;

    public ReverseChargebackOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                      InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments, ChargebackRepository chargebacks, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.chargebacks = chargebacks;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REVERSE_CHARGEBACK;
    }

    @Override
    public Class<ReverseChargebackCommand> contextType() {
        return ReverseChargebackCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(ChargebackReversedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(ReverseChargebackCommand command) {
    }

    @Override
    public PaymentReversalResult processOperation(ReverseChargebackCommand command) {
        LocalDate today = LocalDate.now(clock);
        Long paymentId = chargebacks.findPaymentIdById(command.chargebackId()).orElseThrow(ChargebackNotFoundException::new);
        Long accountId = payments.findStudentCurrentAccountIdById(paymentId).orElseThrow(PaymentNotFoundException::new);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(accountId);
        Chargeback chargeback = chargebacks.findById(command.chargebackId()).orElseThrow(ChargebackNotFoundException::new);
        Payment payment = payments.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
        chargeback.reverseOn(today, command.actor());
        payment.reverseChargeback(command.actor());
        chargebacks.save(chargeback);
        payments.save(payment);
        accountService.recordPaymentReceivedCredit(account, paymentId, chargeback.getAmount(), today, "Contracargo revertido del pago " + paymentId);
        AccountCreditAllocationResult reallocation = allocationService.allocateAccountCreditToOldestInvoices(account, today, command.actor());
        log.warn("Contracargo revertido: pago={}, contracargo={}, cuenta={}, monto={}, fechaDeLaReversion={}, facturasPagadas={}, actor={}", paymentId, chargeback.getId(),
                accountId, chargeback.getAmount().value().amount(), today, reallocation.settledInvoices().stream().map(Invoice::getId).toList(), command.actor());
        return new PaymentReversalResult(paymentId, chargeback.getId(), accountId, payment.getStatus(), chargeback.getAmount(), chargeback.getChargedBackOn(), today, today,
                chargeback.getGatewayReference(), List.of(), reallocation.settledInvoices().stream().map(Invoice::getId).toList());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ReverseChargebackCommand command, PaymentReversalResult result) {
        return List.of(ChargebackReversedEvent.reversedFor(result.paymentId(), result.chargebackId(), result.studentCurrentAccountId(), result.reversedOn(), result.amount()));
    }
}
