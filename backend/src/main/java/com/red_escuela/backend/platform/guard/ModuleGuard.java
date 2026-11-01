package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.platform.exception.UnknownGuardedModuleException;
import org.springframework.stereotype.Component;

@Component
public class ModuleGuard {

    private final BillingModeGate billingModeGate;

    public ModuleGuard(BillingModeGate billingModeGate) {
        this.billingModeGate = billingModeGate;
    }

    public boolean permits(RequiresModule requirement) {
        if (RequiresModule.BILLING.equals(requirement.value())) {
            return billingModeGate.allows(requirement.minMode());
        }
        throw new UnknownGuardedModuleException(requirement.value());
    }
}
