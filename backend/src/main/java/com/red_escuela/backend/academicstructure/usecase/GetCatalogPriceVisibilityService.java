package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;

@UseCase
public class GetCatalogPriceVisibilityService implements GetCatalogPriceVisibilityUseCase {

    private final CurrentBillingModePort billingMode;

    public GetCatalogPriceVisibilityService(CurrentBillingModePort billingMode) {
        this.billingMode = billingMode;
    }

    @Override
    public boolean execute() {
        return billingMode.currentMode().isEnabled();
    }
}
