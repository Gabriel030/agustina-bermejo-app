package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.PaymentStanding;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentStandingResolverTest {

    private static final Long ENROLLMENT_ID = 5L;
    private static final Long STUDENT_ID = 9L;

    private final BillingModeGate gate = mock(BillingModeGate.class);
    private final OverdueDebtProbe probe = mock(OverdueDebtProbe.class);
    private final RegularEnrollmentFactsFinder factsFinder = mock(RegularEnrollmentFactsFinder.class);
    private final PaymentStandingResolver resolver = new PaymentStandingResolver(gate, probe, factsFinder);

    PaymentStandingResolverTest() {
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(STUDENT_ID);
        when(factsFinder.find(RegularityKind.PROGRAM, ENROLLMENT_ID))
                .thenReturn(new RegularEnrollmentFacts(student, "la carrera", "Carrera"));
    }

    @Test
    void billingOffAndMirrorLeavePaymentsToConfirm() {
        for (BillingMode mode : new BillingMode[]{BillingMode.OFF, BillingMode.MIRROR}) {
            when(gate.currentMode()).thenReturn(mode);

            assertThat(resolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID)).isEqualTo(PaymentStanding.TO_CONFIRM);
        }
        verifyNoInteractions(probe);
    }

    @Test
    void billingFullWithFeeSettledAndNoDebtIsVerified() {
        when(gate.currentMode()).thenReturn(BillingMode.FULL);
        when(probe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(probe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        assertThat(resolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID)).isEqualTo(PaymentStanding.VERIFIED);
    }

    @Test
    void billingFullWithOverdueDebtIsOverdue() {
        when(gate.currentMode()).thenReturn(BillingMode.FULL);
        when(probe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(true);
        when(probe.hasOverdueDebt(STUDENT_ID)).thenReturn(true);

        assertThat(resolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID)).isEqualTo(PaymentStanding.OVERDUE);
    }

    @Test
    void billingFullWithUnsettledEnrollmentFeeIsOverdue() {
        when(gate.currentMode()).thenReturn(BillingMode.FULL);
        when(probe.isEnrollmentFeePaid(RegularityKind.PROGRAM, ENROLLMENT_ID)).thenReturn(false);
        when(probe.hasOverdueDebt(STUDENT_ID)).thenReturn(false);

        assertThat(resolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID)).isEqualTo(PaymentStanding.OVERDUE);
    }

    @Test
    void nonFullModeNeverLooksUpTheStudent() {
        when(gate.currentMode()).thenReturn(BillingMode.OFF);

        resolver.resolve(RegularityKind.PROGRAM, ENROLLMENT_ID);

        verify(factsFinder, never()).find(RegularityKind.PROGRAM, ENROLLMENT_ID);
    }
}
