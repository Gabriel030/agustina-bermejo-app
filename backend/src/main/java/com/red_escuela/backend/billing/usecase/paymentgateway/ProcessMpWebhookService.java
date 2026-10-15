package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent;
import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentCreditingResult;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.service.ApprovedPaymentCreditService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@UseCase
public class ProcessMpWebhookService implements ProcessMpWebhookUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessMpWebhookService.class);
    private static final String APPROVED_RAW_STATUS = "approved";

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final StudentCurrentAccountService accountService;
    private final ApprovedPaymentCreditService creditService;
    private final MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher;
    private final MpWebhookSignatureVerifier signatureVerifier;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ProcessMpWebhookService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            StudentCurrentAccountService accountService,
            ApprovedPaymentCreditService creditService,
            MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher,
            MpWebhookSignatureVerifier signatureVerifier,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
        this.creditService = creditService;
        this.mercadoPagoPaymentFetcher = mercadoPagoPaymentFetcher;
        this.signatureVerifier = signatureVerifier;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void process(String xSignature, String xRequestId, String mpPaymentId) {
        try {
            signatureVerifier.verify(xSignature, xRequestId, mpPaymentId);
        } catch (InvalidMpWebhookSignatureException e) {
            log.warn("Webhook de Mercado Pago rechazado por firma inválida: pagoMp={}", mpPaymentId);
            throw e;
        }

        paymentRepository.lockGatewayReferenceUntilTransactionEnds(mpPaymentId);
        if (paymentRepository.existsByGatewayAndGatewayPaymentId(PaymentGatewayCode.MERCADO_PAGO, mpPaymentId)) {
            log.warn("Webhook de Mercado Pago ignorado, el pago ya estaba acreditado: pagoMp={}", mpPaymentId);
            return;
        }

        Optional<MercadoPagoApprovedPayment> approved = mercadoPagoPaymentFetcher.fetchApproved(mpPaymentId);
        if (approved.isEmpty()) {
            log.info("Webhook de Mercado Pago ignorado, el pago no está aprobado: pagoMp={}", mpPaymentId);
            return;
        }

        PaymentAllocationRequest reportedAllocation = approved.get().reportedAllocation();
        Money received = reportedAllocation.amountApplied();
        if (received.isNegative() || received.isZero()) {
            throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + reportedAllocation.invoiceId() + ")");
        }
        Long invoiceId = reportedAllocation.invoiceId();
        Person owner = invoiceRepository.findOwnerPersonByInvoiceId(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        Long accountId = invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        LocalDate today = LocalDate.now(clock);
        StatusChangeActor actor = StatusChangeActor.ofPerson(owner.getId());
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        if (received.currency() != account.getCurrency()) {
            throw new CurrencyMismatchException(account.getCurrency(), received.currency());
        }

        Payment payment = paymentRepository.save(Payment.registerApprovedGatewayPayment(account, NonNegativeMoney.of(received), PaymentGatewayCode.MERCADO_PAGO,
                new CardPaymentDetails(approved.get().cardType(), approved.get().installments(), null), mpPaymentId,
                new GatewayRawStatus(APPROVED_RAW_STATUS, null), today, actor));

        Invoice reportedInvoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        Set<Long> programAdmissionInvoiceIds = reportedInvoice.getIssuingReason() != null && reportedInvoice.getIssuingReason().isProgramAdmission()
                ? Set.of(reportedInvoice.getId()) : Set.of();
        ApprovedPaymentCreditingResult credited = creditService.creditApprovedPaymentAndAllocate(account, payment, today, actor, programAdmissionInvoiceIds);
        if (credited.leavesCreditInFavor()) {
            eventPublisher.publishEvent(PaymentLeftAsAccountCreditEvent.leftFor(payment.getId(), invoiceId, owner.getId(), credited.leftAsAccountCredit().amount(),
                    received.currency().name()));
        }
        log.info("Webhook de Mercado Pago acreditado: pagoMp={}, factura={}, monto={}", mpPaymentId, invoiceId, received);
    }
}
