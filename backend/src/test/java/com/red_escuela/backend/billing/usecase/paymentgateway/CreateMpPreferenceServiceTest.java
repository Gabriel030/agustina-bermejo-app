package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.GatewayCheckoutRecording;
import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
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
    private final CreateMpPreferenceService service = new CreateMpPreferenceService(
            invoiceRepository, gatewayRegistry, checkoutRecording, "token", "http://front", "http://back");

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

    private Preference preference(String id) {
        Preference preference = Mockito.mock(Preference.class);
        Mockito.when(preference.getId()).thenReturn(id);
        Mockito.when(preference.getInitPoint()).thenReturn("https://mp/init/" + id);
        return preference;
    }

    private PreferenceRequest createdWith(Preference preference) {
        payableArsInvoice();
        try (MockedConstruction<PreferenceClient> construction = Mockito.mockConstruction(PreferenceClient.class,
                (client, context) -> Mockito.when(client.create(Mockito.any(PreferenceRequest.class))).thenReturn(preference))) {
            service.create(INVOICE_ID, STUDENT_ID);
            ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
            try {
                Mockito.verify(construction.constructed().get(0)).create(captor.capture());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return captor.getValue();
        }
    }

    private NewGatewayCheckout recordedCheckout() {
        ArgumentCaptor<NewGatewayCheckout> captor = ArgumentCaptor.forClass(NewGatewayCheckout.class);
        Mockito.verify(checkoutRecording).recordMercadoPagoCheckout(captor.capture());
        return captor.getValue();
    }

    @Test
    void preferenceCarriesOpaqueReferenceNotInvoiceId() {
        PreferenceRequest request = createdWith(preference("pref-1"));

        String reference = request.getExternalReference();
        assertThat(reference).isNotEqualTo(String.valueOf(INVOICE_ID));
        assertThat(UUID.fromString(reference)).isEqualTo(recordedCheckout().publicReference());
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
        try (MockedConstruction<PreferenceClient> ignored = Mockito.mockConstruction(PreferenceClient.class,
                (client, context) -> Mockito.when(client.create(Mockito.any(PreferenceRequest.class))).thenThrow(new MPException("down")))) {
            assertThatThrownBy(() -> service.create(INVOICE_ID, STUDENT_ID)).isInstanceOf(MercadoPagoGatewayException.class);
        }

        Mockito.verifyNoInteractions(checkoutRecording);
    }

    @Test
    void checkoutSaveFailureAfterPreferenceCreationIsMappedToGatewayException() {
        payableArsInvoice();
        Preference orphan = preference("orphan");
        Mockito.doThrow(new IllegalStateException("db down")).when(checkoutRecording)
                .recordMercadoPagoCheckout(Mockito.any(NewGatewayCheckout.class));
        try (MockedConstruction<PreferenceClient> ignored = Mockito.mockConstruction(PreferenceClient.class,
                (client, context) -> Mockito.when(client.create(Mockito.any(PreferenceRequest.class))).thenReturn(orphan))) {
            assertThatThrownBy(() -> service.create(INVOICE_ID, STUDENT_ID))
                    .isInstanceOf(MercadoPagoGatewayException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }
}
