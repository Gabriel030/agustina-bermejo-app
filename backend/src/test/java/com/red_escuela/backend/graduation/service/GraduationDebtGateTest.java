package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationDebtGateTest {

    private GraduationDebtGate gateWhileBillingIs(BillingMode mode) {
        BillingModeGate billingModeGate = Mockito.mock(BillingModeGate.class);
        Mockito.when(billingModeGate.allows(Mockito.any())).thenAnswer(call -> mode.isAtLeast(call.getArgument(0)));
        return new GraduationDebtGate(billingModeGate);
    }

    @Test
    void debtIsNotCheckedWhileBillingIsOff() {
        assertThat(gateWhileBillingIs(BillingMode.OFF).mustCheckDebt()).isFalse();
    }

    @Test
    void debtIsCheckedWhileBillingMirrorsOrIsFull() {
        assertThat(gateWhileBillingIs(BillingMode.MIRROR).mustCheckDebt()).isTrue();
        assertThat(gateWhileBillingIs(BillingMode.FULL).mustCheckDebt()).isTrue();
    }
}
