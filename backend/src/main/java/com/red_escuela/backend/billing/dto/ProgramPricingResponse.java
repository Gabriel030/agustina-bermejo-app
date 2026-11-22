package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.module.CatalogPriced;

public record ProgramPricingResponse(Long programId, Money matriculaCohorte1, Money matriculaCohorte2, Money cuotaMensual)
        implements CatalogPriced<ProgramPricingResponse> {

    @Override
    public ProgramPricingResponse withoutPrices() {
        return new ProgramPricingResponse(programId, null, null, null);
    }
}
