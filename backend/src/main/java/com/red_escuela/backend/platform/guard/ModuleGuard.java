package com.red_escuela.backend.platform.guard;

import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.platform.api.FeatureQuery;
import com.red_escuela.backend.platform.exception.ModuleNotEnabledException;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.beans.factory.annotation.Autowired;
import com.red_escuela.backend.platform.exception.UnknownGuardedModuleException;
import org.springframework.stereotype.Component;

@Component
public class ModuleGuard {

    private final BillingModeGate billingModeGate;

    private final FeatureQuery featureQuery;

    @Autowired
    public ModuleGuard(BillingModeGate billingModeGate, FeatureQuery featureQuery) {
        this.billingModeGate = billingModeGate;
        this.featureQuery = featureQuery;
    }

    public ModuleGuard(BillingModeGate billingModeGate) {
        this(billingModeGate, null);
    }

    public boolean permits(RequiresModule requirement) {
        if (RequiresModule.BILLING.equals(requirement.value())) {
            return billingModeGate.allows(requirement.minMode());
        }
        if (RequiresModule.ANALYTICS.equals(requirement.value())) {
            if (featureQuery == null || !featureQuery.isEnabled(FeatureCode.ANALYTICS)) {
                throw new ModuleNotEnabledException(RequiresModule.ANALYTICS);
            }
            return true;
        }
        throw new UnknownGuardedModuleException(requirement.value());
    }
}
