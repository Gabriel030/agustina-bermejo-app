package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceLockingService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
public class ApproveBankTransferReceiptOperation implements BillingOperation<ApproveBankTransferReceiptCommand, ApprovedPaymentRegistrationResult> {
    private static final Logger log = LoggerFactory.getLogger(ApproveBankTransferReceiptOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceLockingService invoiceLocking;
    private final PaymentRepository payments;
    private final Clock clock;

    public ApproveBankTransferReceiptOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                               InvoiceLockingService invoiceLocking, PaymentRepository payments, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceLocking = invoiceLocking;
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.APPROVE_BANK_TRANSFER_RECEIPT_PAYMENT;
    }

    @Override
    public Class<ApproveBankTransferReceiptCommand> contextType() {
        return ApproveBankTransferReceiptCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(ApproveBankTransferReceiptCommand command) {
    }

    @Override
    public ApprovedPaymentRegistrationResult processOperation(ApproveBankTransferReceiptCommand command) {
        LocalDate today = LocalDate.now(clock);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(command.studentCurrentAccountId());
        Invoice invoice = invoiceLocking.lockInvoiceForUpdate(command.invoiceId());
        invoice.requirePayable();
        NonNegativeMoney verifiedAmount = NonNegativeMoney.of(invoice.getAmounts().outstandingAmount().value());
        Payment payment = Payment.createPendingBankTransferPayment(account, verifiedAmount, new BankTransferPaymentDetails(null, null, null, null, null), null,
                command.reviewer());
        payment.approvePayment(verifiedAmount, today, command.reviewer(), null);
        payment = payments.save(payment);
        accountService.recordPaymentReceivedCredit(account, payment.getId(), verifiedAmount, today, "Pago " + payment.getId());
        Set<Long> programAdmissionInvoiceIds = command.programAdmission() ? Set.of(command.invoiceId()) : Set.of();
        allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(account, today, command.reviewer(), programAdmissionInvoiceIds);
        log.info("Pago por transferencia aprobado y acreditado: pago={}, factura={}, monto={}", payment.getId(), command.invoiceId(), verifiedAmount.value().amount());
        return new ApprovedPaymentRegistrationResult(payment.getId(), account.getId(), today, verifiedAmount, command.invoiceId(),
                Money.zero(verifiedAmount.value().currency()), command.reviewer().personId());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ApproveBankTransferReceiptCommand command, ApprovedPaymentRegistrationResult result) {
        return List.of(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.paidOn(), result.amount()));
    }
}
