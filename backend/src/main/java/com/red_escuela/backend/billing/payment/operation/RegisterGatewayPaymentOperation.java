package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.GatewayPaymentArrivedWithGatewayDisabledEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class RegisterGatewayPaymentOperation implements BillingOperation<RegisterGatewayPaymentCommand, ApprovedPaymentRegistrationResult> {
    private static final Logger log = LoggerFactory.getLogger(RegisterGatewayPaymentOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final PaymentRepository payments;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final Clock clock;

    public RegisterGatewayPaymentOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                           PaymentRepository payments, PaymentGatewayRegistry gatewayRegistry, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.payments = payments;
        this.gatewayRegistry = gatewayRegistry;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REGISTER_GATEWAY_PAYMENT;
    }

    @Override
    public Class<RegisterGatewayPaymentCommand> contextType() {
        return RegisterGatewayPaymentCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, GatewayPaymentArrivedWithGatewayDisabledEvent.class, PaymentLeftAsAccountCreditEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(RegisterGatewayPaymentCommand command) {
        if (command.amount().isNegative() || command.amount().isZero()) {
            throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + command.reportedInvoiceId() + ")");
        }
    }

    @Override
    public ApprovedPaymentRegistrationResult processOperation(RegisterGatewayPaymentCommand command) {
        LocalDate today = LocalDate.now(clock);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(command.studentCurrentAccountId());
        NonNegativeMoney amount = NonNegativeMoney.of(command.amount());
        Payment payment = payments.save(Payment.registerApprovedGatewayPayment(account, amount, command.gateway(), command.details(), command.gatewayPaymentId(),
                command.rawStatus(), today, command.actor()));
        accountService.recordPaymentReceivedCredit(account, payment.getId(), amount, today, "Pago " + payment.getId());
        AccountCreditAllocationResult allocated = allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(account, today, command.actor(),
                command.programAdmissionInvoiceIds());
        Money unallocatedAfterwards = accountService.creditAvailableForAllocation(account).value();
        Money leftAsCredit = unallocatedAfterwards.isGreaterThan(command.amount()) ? command.amount() : unallocatedAfterwards;
        log.info("Pago de pasarela acreditado en la cuenta corriente: pago={}, pasarela={}, monto={}, factura={}, facturas saldadas={}", payment.getId(),
                command.gateway(), command.amount().amount(), command.reportedInvoiceId(), allocated.settledInvoices().stream().map(invoice -> invoice.getId()).toList());
        return new ApprovedPaymentRegistrationResult(payment.getId(), account.getId(), today, amount, command.reportedInvoiceId(), leftAsCredit,
                command.actor().personId(), command.gateway() == PaymentGatewayCode.MERCADO_PAGO && !gatewayRegistry.isEnabledByConfiguration(command.gateway()));
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RegisterGatewayPaymentCommand command, ApprovedPaymentRegistrationResult result) {
        List<DomainEvent> events = new ArrayList<>();
        events.add(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.paidOn(), result.amount()));
        if (result.arrivedWithGatewayDisabled()) {
            events.add(GatewayPaymentArrivedWithGatewayDisabledEvent.arrivedFor(result.paymentId(), result.reportedInvoiceId(), command.gateway().name(),
                    result.amount().value().amount(), result.amount().value().currency().name()));
        }
        if (result.leavesCreditInFavor()) {
            events.add(PaymentLeftAsAccountCreditEvent.leftFor(result.paymentId(), result.reportedInvoiceId(), result.actorPersonId(),
                    result.leftAsAccountCredit().amount(), result.leftAsAccountCredit().currency().name()));
        }
        return events;
    }
}
