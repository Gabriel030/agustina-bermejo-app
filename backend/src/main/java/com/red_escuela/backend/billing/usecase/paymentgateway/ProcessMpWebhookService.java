package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@UseCase
public class ProcessMpWebhookService implements ProcessMpWebhookUseCase {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final String accessToken;
    private final String webhookSecret;

    public ProcessMpWebhookService(
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            RegisterPaymentUseCase registerPaymentUseCase,
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.webhook-secret:}") String webhookSecret) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.accessToken = accessToken;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public void process(String xSignature, String xRequestId, String mpPaymentId) {
        validateSignature(xSignature, xRequestId, mpPaymentId);

        if (paymentRepository.existsByExternalReference(mpPaymentId)) {
            return;
        }

        Payment mpPayment;
        try {
            mpPayment = new PaymentClient().get(Long.parseLong(mpPaymentId));
        } catch (MPException | MPApiException e) {
            throw new MercadoPagoGatewayException("Error al consultar pago en Mercado Pago", e);
        }

        if (!"approved".equals(mpPayment.getStatus())) {
            return;
        }

        String externalRef = mpPayment.getExternalReference();
        Long invoiceId = Long.parseLong(externalRef);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return;
        }

        Person person = resolveOwnerPerson(invoice);
        BigDecimal amount = invoice.getRemainingBalance();

        RegisterPaymentRequest request = new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoiceId, amount)),
                PaymentMethod.MERCADO_PAGO,
                false,
                null,
                mpPaymentId,
                mpPayment.getInstallments(),
                null
        );

        registerPaymentUseCase.register(request, person);
    }

    private void validateSignature(String xSignature, String xRequestId, String mpPaymentId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return;
        }
        try {
            String ts = null;
            String v1 = null;
            for (String part : xSignature.split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    if ("ts".equals(kv[0])) ts = kv[1];
                    else if ("v1".equals(kv[0])) v1 = kv[1];
                }
            }
            if (ts == null || v1 == null) {
                throw new SecurityException("Firma de webhook inválida");
            }
            String template = "id:" + mpPaymentId + ";request-id:" + xRequestId + ";ts:" + ts + ";";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(template.getBytes(StandardCharsets.UTF_8)));
            if (!computed.equals(v1)) {
                throw new SecurityException("Firma de webhook inválida");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Error al validar firma de webhook", e);
        }
    }

    private Person resolveOwnerPerson(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            return invoice.getCourseEnrollment().getStudent().getPerson();
        }
        if (invoice.getProgramEnrollment() != null) {
            return invoice.getProgramEnrollment().getStudent().getPerson();
        }
        throw new IllegalStateException("Invoice sin enrollment asociado: " + invoice.getId());
    }
}
