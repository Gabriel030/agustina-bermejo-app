package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.PaymentStanding;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.specialist.PaymentStandingResolver;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularityEvaluator;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckRegularityServiceTest {

    private static final Long ENROLLMENT_ID = 5L;

    private final RegularityEvaluator evaluator = mock(RegularityEvaluator.class);
    private final BillingModeGate gate = mock(BillingModeGate.class);
    private final PaymentStandingResolver paymentResolver = mock(PaymentStandingResolver.class);
    private final CheckRegularityService service = new CheckRegularityService(evaluator, gate, paymentResolver);

    @Test
    void regularWithPaymentsToConfirmStaysInstitutionallyRegularAndFlagsThePayments() {
        givenVerdict(true, List.of());
        givenPayments(PaymentStanding.TO_CONFIRM);

        RegularityResponse response = service.check(RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThat(response.institutionallyRegular()).isTrue();
        assertThat(response.regular()).isTrue();
        assertThat(response.paymentStanding()).isEqualTo(PaymentStanding.TO_CONFIRM);
    }

    @Test
    void overduePaymentsMakeTheStudentNotInstitutionallyRegularWhileTheLegacyFieldKeepsItsMeaning() {
        givenVerdict(true, List.of());
        givenPayments(PaymentStanding.OVERDUE);

        RegularityResponse response = service.check(RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThat(response.institutionallyRegular()).isFalse();
        assertThat(response.regular()).isTrue();
        assertThat(response.paymentStanding()).isEqualTo(PaymentStanding.OVERDUE);
    }

    @Test
    void freeByAbsencesIsNotInstitutionallyRegularEvenWithPaymentsVerified() {
        givenVerdict(false, List.of(RegularityReason.FREE_IN_ACTIVE_SECTION));
        givenPayments(PaymentStanding.VERIFIED);

        RegularityResponse response = service.check(RegularityKind.PROGRAM, ENROLLMENT_ID);

        assertThat(response.institutionallyRegular()).isFalse();
        assertThat(response.regular()).isFalse();
        assertThat(response.reasons()).containsExactly(RegularityReason.FREE_IN_ACTIVE_SECTION);
    }

    private void givenVerdict(boolean regular, List<RegularityReason> reasons) {
        when(evaluator.evaluate(RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(new RegularityVerdict(RegularityKind.PROGRAM, regular, reasons, true, Instant.EPOCH));
        when(gate.currentMode()).thenReturn(BillingMode.OFF);
    }

    private void givenPayments(PaymentStanding standing) {
        when(paymentResolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(standing);
    }
}
