package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.GatewayCheckoutRecording;
import com.red_escuela.backend.billing.operation.CreatedMercadoPagoPreference;
import com.red_escuela.backend.billing.operation.MercadoPagoPreferenceGateway;
import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.operation.NewMercadoPagoPreference;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateMpPreferenceServiceTest {

    private static final long INVOICE_ID = 4321L;
    private static final long STUDENT_ID = 77L;

    private final InvoiceRepository invoiceRepository = Mockito.mock(InvoiceRepository.class);
    private final PaymentGatewayRegistry gatewayRegistry = Mockito.mock(PaymentGatewayRegistry.class);
    private final GatewayCheckoutRecording checkoutRecording = Mockito.mock(GatewayCheckoutRecording.class);
    private final MercadoPagoPreferenceGateway preferenceGateway = Mockito.mock(MercadoPagoPreferenceGateway.class);
    private final CreateMpPreferenceService service = new CreateMpPreferenceService(
            invoiceRepository, gatewayRegistry, checkoutRecording, preferenceGateway, "http://front", "http://back");

    private Invoice payableArsInvoice() {
        Invoice invoice = Mockito.mock(Invoice.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(invoice.getId()).thenReturn(INVOICE_ID);
        Mockito.when(invoice.isPayable()).thenReturn(true);
        Mockito.when(invoice.currency()).thenReturn(Currency.ARS);
        Mockito.when(invoice.getFeeType()).thenReturn(FeeType.MONTHLY_INSTALLMENT);
        Mockito.when(invoice.getPeriod()).thenReturn(YearMonth.of(2026, 10));
        Mockito.when(invoice.getAmounts().outstandingAmount()).thenReturn(NonNegativeMoney.of(new BigDecimal("1500.50"), Currency.ARS));
        Mockito.when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        Mockito.when(invoiceRepository.findOwnerStudentIdByInvoiceId(INVOICE_ID)).thenReturn(Optional.of(STUDENT_ID));
        return invoice;
    }

    private CreatedMercadoPagoPreference preference(String id) {
        return new CreatedMercadoPagoPreference(id, "https://mp/init/" + id);
    }

    private NewMercadoPagoPreference createdWith(CreatedMercadoPagoPreference preference) {
        payableArsInvoice();
        Mockito.when(preferenceGateway.createPreference(Mockito.any(NewMercadoPagoPreference.class))).thenReturn(preference);
        service.create(INVOICE_ID, STUDENT_ID);
        ArgumentCaptor<NewMercadoPagoPreference> captor = ArgumentCaptor.forClass(NewMercadoPagoPreference.class);
        Mockito.verify(preferenceGateway).createPreference(captor.capture());
        return captor.getValue();
    }

    private NewGatewayCheckout recordedCheckout() {
        ArgumentCaptor<NewGatewayCheckout> captor = ArgumentCaptor.forClass(NewGatewayCheckout.class);
        Mockito.verify(checkoutRecording).recordMercadoPagoCheckout(captor.capture());
        return captor.getValue();
    }

    @Test
    void preferenceCreationGoesThroughTheGatewayPort() {
        NewMercadoPagoPreference request = createdWith(preference("pref-0"));

        assertThat(request.title()).isEqualTo("MONTHLY_INSTALLMENT 2026-10");
        assertThat(request.amount()).isEqualByComparingTo("1500.50");
        assertThat(request.successUrl()).isEqualTo("http://front/alumno/pagos/estado?status=success&invoiceId=" + INVOICE_ID);
        assertThat(request.failureUrl()).isEqualTo("http://front/alumno/pagos/estado?status=failure&invoiceId=" + INVOICE_ID);
        assertThat(request.pendingUrl()).isEqualTo("http://front/alumno/pagos/estado?status=pending&invoiceId=" + INVOICE_ID);
        assertThat(request.notificationUrl()).isEqualTo("http://back/payments/mp-webhook");
    }

    @Test
    void preferenceCarriesOpaqueReferenceNotInvoiceId() {
        NewMercadoPagoPreference request = createdWith(preference("pref-1"));

        assertThat(request.publicReference()).isEqualTo(recordedCheckout().publicReference());
        assertThat(request.publicReference().toString()).isNotEqualTo(String.valueOf(INVOICE_ID));
    }

    @Test
    void checkoutStoresAmountCurrencyAndPreferenceId() {
        createdWith(preference("pref-2"));

        NewGatewayCheckout checkout = recordedCheckout();
        assertThat(checkout.invoiceId()).isEqualTo(INVOICE_ID);
        assertThat(checkout.amount().value().amount()).isEqualByComparingTo("1500.50");
        assertThat(checkout.amount().value().currency()).isEqualTo(Currency.ARS);
        assertThat(checkout.gatewayPreferenceId()).isEqualTo("pref-2");
    }

    @Test
    void failedPreferenceCreationLeavesNoCheckout() {
        payableArsInvoice();
        Mockito.when(preferenceGateway.createPreference(Mockito.any(NewMercadoPagoPreference.class)))
                .thenThrow(new MercadoPagoGatewayException("down", new RuntimeException()));

        assertThatThrownBy(() -> service.create(INVOICE_ID, STUDENT_ID)).isInstanceOf(MercadoPagoGatewayException.class);

        Mockito.verifyNoInteractions(checkoutRecording);
    }

    @Test
    void checkoutSaveFailureAfterPreferenceCreationIsMappedToGatewayException() {
        payableArsInvoice();
        Mockito.when(preferenceGateway.createPreference(Mockito.any(NewMercadoPagoPreference.class))).thenReturn(preference("orphan"));
        Mockito.doThrow(new IllegalStateException("db down")).when(checkoutRecording)
                .recordMercadoPagoCheckout(Mockito.any(NewGatewayCheckout.class));

        assertThatThrownBy(() -> service.create(INVOICE_ID, STUDENT_ID))
                .isInstanceOf(MercadoPagoGatewayException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
