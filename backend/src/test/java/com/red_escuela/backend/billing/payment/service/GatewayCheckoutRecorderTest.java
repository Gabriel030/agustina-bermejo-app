package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.GatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.GatewayCheckoutRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayCheckoutRecorderTest {

    private final GatewayCheckoutRepository repository = Mockito.mock(GatewayCheckoutRepository.class);
    private final GatewayCheckoutRecorder recorder = new GatewayCheckoutRecorder(repository, 48);

    @Test
    @Tag("D-538")
    void checkoutExpiresFortyEightHoursAfterCreation() {
        recorder.recordMercadoPagoCheckout(new NewGatewayCheckout(UUID.randomUUID(), 9L,
                NonNegativeMoney.of(new BigDecimal("10.00"), Currency.ARS), "pref"));

        ArgumentCaptor<GatewayCheckout> captor = ArgumentCaptor.forClass(GatewayCheckout.class);
        Mockito.verify(repository).save(captor.capture());
        GatewayCheckout saved = captor.getValue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(Duration.between(saved.getCreatedAt(), saved.getExpiresAt())).isEqualTo(Duration.ofHours(48));
        assertThat(saved.getGateway()).isEqualTo(PaymentGatewayCode.MERCADO_PAGO);
        assertThat(saved.getInvoiceId()).isEqualTo(9L);
        assertThat(saved.getGatewayPreferenceId()).isEqualTo("pref");
    }
}
