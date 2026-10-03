package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.red_escuela.backend.billing.dto.MpPreferenceResponse;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.shared.UseCase;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.red_escuela.backend.billing.exception.GatewayCurrencyNotSupportedException;
import com.red_escuela.backend.shared.domain.Currency;

import java.math.BigDecimal;
import java.util.List;

@UseCase
public class CreateMpPreferenceService implements CreateMpPreferenceUseCase {
    private static final Logger log = LoggerFactory.getLogger(CreateMpPreferenceService.class);

    private final InvoiceRepository invoiceRepository;
    private final String accessToken;
    private final String frontendBaseUrl;
    private final String backendBaseUrl;

    public CreateMpPreferenceService(
            InvoiceRepository invoiceRepository,
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.backend.base-url:http://localhost:8080}") String backendBaseUrl) {
        this.invoiceRepository = invoiceRepository;
        this.accessToken = accessToken;
        this.frontendBaseUrl = frontendBaseUrl;
        this.backendBaseUrl = backendBaseUrl;
    }

    @PostConstruct
    void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public MpPreferenceResponse create(Long invoiceId, Long studentId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));

        Long ownerStudentId = resolveOwnerStudentId(invoice);
        if (!studentId.equals(ownerStudentId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }

        if (invoice.getStatus() == InvoiceStatus.PAID
                || invoice.getStatus() == InvoiceStatus.CANCELLED
                || invoice.getStatus() == InvoiceStatus.WAIVED) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus().name());
        }

        if (invoice.currency() != Currency.ARS) {
            throw new GatewayCurrencyNotSupportedException(invoiceId, invoice.currency());
        }
        BigDecimal amount = invoice.getRemainingBalance().amount();
        String title = invoice.getFeeType().name() + " " + invoice.getPeriod();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(PreferenceItemRequest.builder()
                        .title(title)
                        .quantity(1)
                        .unitPrice(amount)
                        .currencyId("ARS")
                        .build()))
                .externalReference(String.valueOf(invoiceId))
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(frontendBaseUrl + "/alumno/pagos/estado?status=success&invoiceId=" + invoiceId)
                        .failure(frontendBaseUrl + "/alumno/pagos/estado?status=failure&invoiceId=" + invoiceId)
                        .pending(frontendBaseUrl + "/alumno/pagos/estado?status=pending&invoiceId=" + invoiceId)
                        .build())
                .notificationUrl(backendBaseUrl + "/payments/mp-webhook")
                .build();

        try {
            Preference preference = new PreferenceClient().create(request);
            log.info("Preferencia de Mercado Pago creada: preferencia={}, factura={}, monto={}", preference.getId(), invoiceId, amount);
            return new MpPreferenceResponse(preference.getId(), preference.getInitPoint());
        } catch (MPException | MPApiException e) {
            log.error("Falló la creación de la preferencia de Mercado Pago: factura={}", invoiceId, e);
            throw new MercadoPagoGatewayException("Error al crear preferencia de pago", e);
        }
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            return invoice.getCourseEnrollment().getStudent().getId();
        }
        if (invoice.getProgramEnrollment() != null) {
            return invoice.getProgramEnrollment().getStudent().getId();
        }
        return null;
    }
}
