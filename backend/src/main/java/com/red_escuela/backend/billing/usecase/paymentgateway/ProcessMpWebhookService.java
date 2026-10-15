package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@UseCase
public class ProcessMpWebhookService implements ProcessMpWebhookUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessMpWebhookService.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher;
    private final MpWebhookSignatureVerifier signatureVerifier;

    public ProcessMpWebhookService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            RegisterPaymentUseCase registerPaymentUseCase,
            MercadoPagoPaymentFetcher mercadoPagoPaymentFetcher,
            MpWebhookSignatureVerifier signatureVerifier) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.mercadoPagoPaymentFetcher = mercadoPagoPaymentFetcher;
        this.signatureVerifier = signatureVerifier;
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

        paymentRepository.lockExternalReferenceUntilTransactionEnds(mpPaymentId);
        if (paymentRepository.existsByExternalReference(mpPaymentId)) {
            log.warn("Webhook de Mercado Pago ignorado, el pago ya estaba acreditado: pagoMp={}", mpPaymentId);
            return;
        }

        Optional<MercadoPagoApprovedPayment> approved = mercadoPagoPaymentFetcher.fetchApproved(mpPaymentId);
        if (approved.isEmpty()) {
            log.info("Webhook de Mercado Pago ignorado, el pago no está aprobado: pagoMp={}", mpPaymentId);
            return;
        }

        PaymentAllocationRequest reportedAllocation = approved.get().reportedAllocation();
        Long invoiceId = reportedAllocation.invoiceId();
        Person owner = invoiceRepository.findOwnerPersonByInvoiceId(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        RegisterPaymentRequest request = new RegisterPaymentRequest(
                List.of(reportedAllocation),
                PaymentMethod.MERCADO_PAGO,
                false,
                null,
                mpPaymentId,
                approved.get().installments(),
                null
        );

        Payment payment = registerPaymentUseCase.creditPaymentToAccountAndSettleInvoiceIfCovered(request, owner);
        payment.setCardType(approved.get().cardType());
        log.info("Webhook de Mercado Pago acreditado: pagoMp={}, factura={}, monto={}", mpPaymentId, invoiceId, reportedAllocation.amountApplied());
    }
}
