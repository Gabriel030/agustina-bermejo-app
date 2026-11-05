package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.EffectiveModules;

public record PublicModulesResponse(boolean billingEnabled) {

    public static PublicModulesResponse from(EffectiveModules modules) {
        return new PublicModulesResponse(modules.billingEnabled());
    }
}
