package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;

@Service
public class GraduationDebtGate {

    private final BillingModeGate billingModeGate;

    public GraduationDebtGate(BillingModeGate billingModeGate) {
        this.billingModeGate = billingModeGate;
    }

    public boolean mustCheckDebt() {
        return billingModeGate.allows(BillingMode.MIRROR);
    }
}
