package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentFixtures;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.gateway.PaymentGatewayRegistry;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-256")
class RegisterGatewayPaymentOperationTest {

    private final StudentCurrentAccountService accountService = mock(StudentCurrentAccountService.class);
    private final CurrentAccountAllocationService allocationService = mock(CurrentAccountAllocationService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
    private final Clock clock = Clock.fixed(Instant.parse("2027-03-20T12:00:00Z"), ZoneId.of("UTC"));
    private final RegisterGatewayPaymentOperation operation = new RegisterGatewayPaymentOperation(accountService, allocationService, payments, registry, clock);

    private ApprovedPaymentRegistrationResult register(PaymentGatewayCode gateway, boolean enabledByConfiguration) {
        StudentCurrentAccount account = PaymentFixtures.arsAccount();
        ReflectionTestUtils.setField(account, "id", 10L);
        when(accountService.lockStudentCurrentAccount(10L)).thenReturn(account);
        when(accountService.creditAvailableForAllocation(account)).thenReturn(account.getUnallocatedCreditAmount());
        when(payments.save(any(Payment.class))).thenAnswer(call -> {
            Payment saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        when(allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(any(), any(), any(), any()))
                .thenReturn(AccountCreditAllocationResult.nothingAllocated());
        when(registry.isEnabledByConfiguration(gateway)).thenReturn(enabledByConfiguration);
        RegisterGatewayPaymentCommand command = new RegisterGatewayPaymentCommand(10L, 5L, Set.of(), Money.of(new BigDecimal("1000.00"), Currency.ARS), gateway,
                new CardPaymentDetails(CardPaymentType.CREDIT, 1, null), "gw-1", new GatewayRawStatus("approved", "accredited"), StatusChangeActor.ofProcess("WEBHOOK"));
        return operation.processOperation(command);
    }

    @Test
    void mercadoPagoWithTheGatewayTurnedOffArrivesDisabled() {
        assertThat(register(PaymentGatewayCode.MERCADO_PAGO, false).arrivedWithGatewayDisabled()).isTrue();
    }

    @Test
    void mercadoPagoWithTheGatewayTurnedOnDoesNotArriveDisabled() {
        assertThat(register(PaymentGatewayCode.MERCADO_PAGO, true).arrivedWithGatewayDisabled()).isFalse();
    }

    @Test
    void anotherOnlineGatewayNeverRaisesTheMercadoPagoDisabledNotice() {
        assertThat(register(PaymentGatewayCode.DLOCAL, false).arrivedWithGatewayDisabled()).isFalse();
    }
}
