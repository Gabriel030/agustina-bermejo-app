package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.MpPreferenceResponse;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.operation.GatewayCheckoutRecording;
import com.red_escuela.backend.billing.operation.CreatedMercadoPagoPreference;
import com.red_escuela.backend.billing.operation.MercadoPagoPreferenceGateway;
import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.operation.NewMercadoPagoPreference;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.red_escuela.backend.billing.exception.GatewayCurrencyNotSupportedException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.math.BigDecimal;
import java.util.UUID;

@UseCase
public class CreateMpPreferenceService implements CreateMpPreferenceUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateMpPreferenceService.class);

    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final GatewayCheckoutRecording gatewayCheckoutRecording;
    private final MercadoPagoPreferenceGateway preferenceGateway;
    private final String frontendBaseUrl;
    private final String backendBaseUrl;

    public CreateMpPreferenceService(
            InvoiceRepository invoiceRepository,
            PaymentGatewayRegistry gatewayRegistry,
            GatewayCheckoutRecording gatewayCheckoutRecording,
            MercadoPagoPreferenceGateway preferenceGateway,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.backend.base-url:http://localhost:8080}") String backendBaseUrl) {
        this.invoiceRepository = invoiceRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.gatewayCheckoutRecording = gatewayCheckoutRecording;
        this.preferenceGateway = preferenceGateway;
        this.frontendBaseUrl = frontendBaseUrl;
        this.backendBaseUrl = backendBaseUrl;
    }

    @Override
    public MpPreferenceResponse create(Long invoiceId, Long studentId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        Long ownerStudentId = resolveOwnerStudentId(invoice);
        if (!studentId.equals(ownerStudentId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }

        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
        }

        if (invoice.currency() != Currency.ARS) {
            throw new GatewayCurrencyNotSupportedException(invoiceId, invoice.currency());
        }
        gatewayRegistry.findGatewayByCode(PaymentGatewayCode.MERCADO_PAGO);
        NonNegativeMoney outstanding = invoice.getAmounts().outstandingAmount();
        BigDecimal amount = outstanding.value().amount();
        UUID publicReference = UUID.randomUUID();
        String title = invoice.getFeeType().name() + " " + invoice.getPeriod();

        NewMercadoPagoPreference newPreference = new NewMercadoPagoPreference(
                title,
                amount,
                publicReference,
                frontendBaseUrl + "/alumno/pagos/estado?status=success&invoiceId=" + invoiceId,
                frontendBaseUrl + "/alumno/pagos/estado?status=failure&invoiceId=" + invoiceId,
                frontendBaseUrl + "/alumno/pagos/estado?status=pending&invoiceId=" + invoiceId,
                backendBaseUrl + "/payments/mp-webhook");

        CreatedMercadoPagoPreference preference = preferenceGateway.createPreference(newPreference);
        String preferenceId = preference.id();
        try {
            gatewayCheckoutRecording.recordMercadoPagoCheckout(new NewGatewayCheckout(
                    publicReference, invoiceId, outstanding, preferenceId));
        } catch (RuntimeException e) {
            log.error("Falló el registro del checkout de Mercado Pago: factura={}, preferencia={}", invoiceId, preferenceId, e);
            throw new MercadoPagoGatewayException("Error al crear preferencia de pago", e);
        }
        log.info("Preferencia de Mercado Pago creada: preferencia={}, factura={}, monto={}", preferenceId, invoiceId, amount);
        return new MpPreferenceResponse(preferenceId, preference.initPoint());
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        return invoiceRepository.findOwnerStudentIdByInvoiceId(invoice.getId()).orElse(null);
    }
}
