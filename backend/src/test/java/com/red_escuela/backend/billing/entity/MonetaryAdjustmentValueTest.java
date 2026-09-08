package com.red_escuela.backend.billing.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fase 6.1: MonetaryAdjustmentValue como Value Object polimórfico (sealed interface) —
 * "monto fijo o porcentaje, nunca los dos" es una invariante del tipo en sí.
 */
class MonetaryAdjustmentValueTest {

    // 53. fixedAmountNegativoSeRechazaEnLaConstrucción
    @Test
    void fixedAmountNegativoSeRechazaEnLaConstrucción() {
        assertThatThrownBy(() -> new MonetaryAdjustmentValue.FixedAmount(BigDecimal.valueOf(-100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 54. percentageFueraDeRangoSeRechaza
    @Test
    void percentageFueraDeRangoSeRechaza() {
        assertThatThrownBy(() -> new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("1.5")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("-0.1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 57. resolveContraDistintasBasesDaElResultadoEsperado
    @Test
    void resolveContraDistintasBasesDaElResultadoEsperado() {
        MonetaryAdjustmentValue fixed = new MonetaryAdjustmentValue.FixedAmount(BigDecimal.valueOf(500));
        assertThat(fixed.resolve(BigDecimal.valueOf(999999))).isEqualByComparingTo(BigDecimal.valueOf(500));

        MonetaryAdjustmentValue percentage = new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.3"));
        assertThat(percentage.resolve(BigDecimal.valueOf(1000))).isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    void percentageResuelveSiempreConEscalaDosYRedondeoHalfUp() {
        MonetaryAdjustmentValue percentage = new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.335"));
        BigDecimal resultado = percentage.resolve(new BigDecimal("100.005"));
        assertThat(resultado.scale()).isEqualTo(2);
    }
}
