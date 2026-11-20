package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.PaymentStanding;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Component;

@Component
public class PaymentStandingResolver {

    private final BillingModeGate billingModeGate;
    private final OverdueDebtProbe overdueDebtProbe;
    private final RegularEnrollmentFactsFinder enrollmentFactsFinder;

    public PaymentStandingResolver(BillingModeGate billingModeGate, OverdueDebtProbe overdueDebtProbe,
                                   RegularEnrollmentFactsFinder enrollmentFactsFinder) {
        this.billingModeGate = billingModeGate;
        this.overdueDebtProbe = overdueDebtProbe;
        this.enrollmentFactsFinder = enrollmentFactsFinder;
    }

    public PaymentStanding resolve(RegularityKind kind, Long enrollmentId) {
        if (billingModeGate.currentMode() != BillingMode.FULL) {
            return PaymentStanding.TO_CONFIRM;
        }
        Long studentId = enrollmentFactsFinder.find(kind, enrollmentId).student().getId();
        boolean settled = overdueDebtProbe.isEnrollmentFeePaid(kind, enrollmentId) && !overdueDebtProbe.hasOverdueDebt(studentId);
        return settled ? PaymentStanding.VERIFIED : PaymentStanding.OVERDUE;
    }
}
