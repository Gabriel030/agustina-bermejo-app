package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.LateFeePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Puro (sin Spring) — mismo criterio que PricingCalculatorTest. */
class LateFeeCalculatorTest {

    private LateFeePolicy policy(int diasDeGracia, double porcentajePorPeriodo, int diasPorPeriodo, int diasParaRiesgo) {
        LateFeePolicy policy = new LateFeePolicy();
        policy.setDiasDeGracia(diasDeGracia);
        policy.setPorcentajeRecargoPorPeriodo(BigDecimal.valueOf(porcentajePorPeriodo));
        policy.setDiasPorPeriodo(diasPorPeriodo);
        policy.setDiasParaRiesgoDeCorte(diasParaRiesgo);
        return policy;
    }

    @Test
    void dentroDeLosDiasDeGraciaNoEstaEnMora() {
        LateFeePolicy p = policy(5, 2, 5, 10);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        assertThat(LateFeeCalculator.estaEnMora(p, dueDate, dueDate.plusDays(5))).isFalse();
    }

    @Test
    void pasadosLosDiasDeGraciaEstaEnMora() {
        LateFeePolicy p = policy(5, 2, 5, 10);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        assertThat(LateFeeCalculator.estaEnMora(p, dueDate, dueDate.plusDays(6))).isTrue();
    }

    @Test
    void sinPeriodosCompletosDeAtrasoElRecargoEsCero() {
        LateFeePolicy p = policy(5, 2, 5, 10);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        // 6 días de atraso, 5 de gracia → 1 día de mora, no llega a un período de 5.
        BigDecimal recargo = LateFeeCalculator.calcularRecargo(p, dueDate, dueDate.plusDays(6), BigDecimal.valueOf(1000));
        assertThat(recargo).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void unPeriodoCompletoAplicaElPorcentajeUnaVez() {
        LateFeePolicy p = policy(5, 2, 5, 20);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        // 10 días de atraso, 5 de gracia → 5 días de mora → exactamente 1 período de 5.
        BigDecimal recargo = LateFeeCalculator.calcularRecargo(p, dueDate, dueDate.plusDays(10), BigDecimal.valueOf(1000));
        assertThat(recargo).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void dosPeriodosCompletosAplicanElPorcentajeAcumulado() {
        LateFeePolicy p = policy(5, 2, 5, 20);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        // 15 días de atraso, 5 de gracia → 10 días de mora → 2 períodos de 5 → 4% total.
        BigDecimal recargo = LateFeeCalculator.calcularRecargo(p, dueDate, dueDate.plusDays(15), BigDecimal.valueOf(1000));
        assertThat(recargo).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    @Test
    void riesgoDeCorteSeActivaAlLlegarAlUmbralConfigurado() {
        LateFeePolicy p = policy(5, 2, 5, 10);
        LocalDate dueDate = LocalDate.of(2026, 1, 1);
        assertThat(LateFeeCalculator.estaEnRiesgoDeCorte(p, dueDate, dueDate.plusDays(9))).isFalse();
        assertThat(LateFeeCalculator.estaEnRiesgoDeCorte(p, dueDate, dueDate.plusDays(10))).isTrue();
    }
}
