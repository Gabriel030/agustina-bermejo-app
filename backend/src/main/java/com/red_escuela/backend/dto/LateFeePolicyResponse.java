package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.LateFeePolicy;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LateFeePolicyResponse(
        Long id, Integer diasDeGracia, BigDecimal porcentajeRecargoPorPeriodo,
        Integer diasPorPeriodo, Integer diasParaRiesgoDeCorte, LocalDate vigenteDesde) {

    public static LateFeePolicyResponse from(LateFeePolicy policy) {
        return new LateFeePolicyResponse(
                policy.getId(), policy.getGracePeriodDays(), policy.getSurchargePercentagePerPeriod(),
                policy.getPeriodDays(), policy.getDaysUntilCutoffRisk(), policy.getValidFrom());
    }
}
