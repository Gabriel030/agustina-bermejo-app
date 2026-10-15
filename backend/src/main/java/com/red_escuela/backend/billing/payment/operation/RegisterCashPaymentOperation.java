package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.billing.payment.policy.CashPaymentCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentPreviewCheck;
import com.red_escuela.backend.billing.payment.policy.CashPaymentRejections;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.service.CashPaymentPlanner;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class RegisterCashPaymentOperation implements BillingOperation<RegisterCashPaymentCommand, CashPaymentRegistrationResult> {
    private static final Logger log = LoggerFactory.getLogger(RegisterCashPaymentOperation.class);

    private final PolicySet<CashPaymentCheck> registrationPolicies;
    private final PolicySet<CashPaymentCheck> datingPolicies;
    private final PolicySet<CashPaymentPreviewCheck> previewPolicies;
    private final StudentExistenceLookup students;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final CashPaymentPlanner planner;
    private final PaymentGatewayRegistry gateways;
    private final PaymentRepository payments;
    private final Clock clock;

    public RegisterCashPaymentOperation(PolicySet<CashPaymentCheck> cashPaymentRegistrationPolicySet, PolicySet<CashPaymentCheck> cashPaymentDatingPolicySet,
                                        PolicySet<CashPaymentPreviewCheck> cashPaymentPreviewPolicySet,
                                        StudentExistenceLookup students, StudentCurrentAccountService accountService,
                                        CurrentAccountAllocationService allocationService, CashPaymentPlanner planner, PaymentGatewayRegistry gateways,
                                        PaymentRepository payments, Clock clock) {
        this.registrationPolicies = cashPaymentRegistrationPolicySet;
        this.datingPolicies = cashPaymentDatingPolicySet;
        this.previewPolicies = cashPaymentPreviewPolicySet;
        this.students = students;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.planner = planner;
        this.gateways = gateways;
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REGISTER_PAYMENT;
    }

    @Override
    public Class<RegisterCashPaymentCommand> contextType() {
        return RegisterCashPaymentCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(registrationPolicies.moment(), datingPolicies.moment(), previewPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(RegisterCashPaymentCommand command) {
        registrationPolicies.evaluateRulesUntilFirstViolation(new CashPaymentCheck(command.amount(), null, null, command.requestedByDirector(),
                command.backdateReason(), command.receiptNumber(), command.idempotencyKey())).throwIfDenied(CashPaymentRejections::rejectionOf);
    }

    @Override
    public CashPaymentRegistrationResult processOperation(RegisterCashPaymentCommand command) {
        LocalDate today = LocalDate.now(clock);
        LocalDate paidOn = paidOnOf(command, today);
        students.requireExistingStudent(command.studentId());
        datingPolicies.evaluateRulesUntilFirstViolation(new CashPaymentCheck(command.amount(), paidOn, today, command.requestedByDirector(),
                command.backdateReason(), command.receiptNumber(), command.idempotencyKey())).throwIfDenied(CashPaymentRejections::rejectionOf);
        Currency currency = command.amount().currency();
        StudentCurrentAccount account = accountService.findOrOpenAndLockStudentCurrentAccount(command.studentId(), currency);
        gateways.findGatewayDeclaring(PaymentGatewayCode.MANUAL, currency, Set.of());
        NonNegativeMoney amount = NonNegativeMoney.of(command.amount());
        planner.lockOpenInvoicesOfLockedAccount(account.getId());
        CashPaymentPlanner.CashPaymentCandidates candidates = planner.findCandidatesOfAccount(account.getId());
        CashPaymentPreview current = planner.previewCashPayment(command.studentId(), currency, candidates, account.getUnallocatedCreditAmount(), amount);
        previewPolicies.evaluateRulesUntilFirstViolation(new CashPaymentPreviewCheck(command.previewFingerprint(), current.fingerprint()))
                .throwIfDenied(CashPaymentRejections::rejectionOf);
        Payment payment = payments.save(Payment.registerApprovedCashPayment(account, amount, paidOn, new CashPaymentDetails(textOrNull(command.receiptNumber())),
                command.idempotencyKey(), command.actor(), textOrNull(command.backdateReason())));
        accountService.recordPaymentReceivedCredit(account, payment.getId(), amount, paidOn, "Pago en efectivo " + payment.getId());
        List<CashPaymentRegistrationResult.AllocatedInvoice> allocatedInvoices = allocatedInvoicesOf(allocationService
                .allocateAccountCreditToOldestInvoicesSkipping(account, today, command.actor(), candidates.skippedInvoiceIds()));
        List<CashPaymentRegistrationResult.AllocatedInvoice> allocatedFromRemainingCredit = allocatedInvoicesOf(allocationService
                .allocateAccountCreditToOldestInvoices(account, today, command.actor()));
        log.info("Pago en efectivo registrado: pago={}, cuenta={}, monto={}, paidOn={}, facturas={}, facturasConSaldoRestante={}, actor={}", payment.getId(), account.getId(),
                amount.value().amount(), paidOn, allocatedInvoices.stream().map(CashPaymentRegistrationResult.AllocatedInvoice::invoiceId).toList(),
                allocatedFromRemainingCredit.stream().map(CashPaymentRegistrationResult.AllocatedInvoice::invoiceId).toList(), command.actor().personId());
        return new CashPaymentRegistrationResult(payment.getId(), account.getId(), amount, paidOn, payment.getRegisteredAt(), allocatedInvoices,
                allocatedFromRemainingCredit, account.getUnallocatedCreditAmount());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RegisterCashPaymentCommand command, CashPaymentRegistrationResult result) {
        return List.of(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.paidOn(), result.amount()));
    }

    private static List<CashPaymentRegistrationResult.AllocatedInvoice> allocatedInvoicesOf(AccountCreditAllocationResult allocated) {
        return IntStream.range(0, allocated.allocations().size())
                .mapToObj(index -> new CashPaymentRegistrationResult.AllocatedInvoice(allocated.settledInvoices().get(index).getId(),
                        allocated.allocations().get(index).getAmount()))
                .toList();
    }

    private static LocalDate paidOnOf(RegisterCashPaymentCommand command, LocalDate today) {
        return command.paidOn() == null ? today : command.paidOn();
    }

    private static String textOrNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
