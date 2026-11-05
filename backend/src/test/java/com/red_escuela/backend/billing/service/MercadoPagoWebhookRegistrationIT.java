package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class MercadoPagoWebhookRegistrationIT extends AbstractIntegrationTest {

    @Autowired
    private MercadoPagoWebhookRegistration webhookRegistration;
    @MockitoBean
    private ApprovedPaymentRegistrationPort registrationPort;
    @MockitoBean
    private MercadoPagoPaymentFetcher fetcher;
    @MockitoBean
    private MpWebhookSignatureVerifier signatureVerifier;

    @Test
    void lockCheckAndRegistrationRunInsideTheSameTransactionOpenedByTheRegistration() {
        List<Boolean> transactionActiveAtEachStep = new ArrayList<>();
        doAnswer(invocation -> {
            transactionActiveAtEachStep.add(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(registrationPort).lockMercadoPagoPaymentUntilTransactionEnds(anyString());
        when(registrationPort.isMercadoPagoPaymentRegistered(anyString())).thenAnswer(invocation -> {
            transactionActiveAtEachStep.add(TransactionSynchronizationManager.isActualTransactionActive());
            return false;
        });
        when(fetcher.fetchApproved(anyString())).thenReturn(Optional.of(
                new MercadoPagoApprovedPayment(new ReportedGatewayAllocation(7L, Money.of(new BigDecimal("100.00"), Currency.ARS)), 1)));
        when(registrationPort.registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString())).thenAnswer(invocation -> {
            transactionActiveAtEachStep.add(TransactionSynchronizationManager.isActualTransactionActive());
            return 1L;
        });

        webhookRegistration.register("", "request-1", "mp-tx-1");

        assertThat(transactionActiveAtEachStep).containsExactly(true, true, true);
    }

    @Test
    void replayedWebhookRegistersApprovedPaymentExactlyOnce() {
        AtomicBoolean registered = new AtomicBoolean(false);
        when(registrationPort.isMercadoPagoPaymentRegistered(anyString())).thenAnswer(invocation -> registered.get());
        when(fetcher.fetchApproved(anyString())).thenReturn(Optional.of(
                new MercadoPagoApprovedPayment(new ReportedGatewayAllocation(7L, Money.of(new BigDecimal("100.00"), Currency.ARS)), 1)));
        when(registrationPort.registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString())).thenAnswer(invocation -> {
            registered.set(true);
            return 1L;
        });

        webhookRegistration.register("", "request-1", "mp-replay-1");
        webhookRegistration.register("", "request-2", "mp-replay-1");

        verify(registrationPort, times(1)).registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString());
    }
}
