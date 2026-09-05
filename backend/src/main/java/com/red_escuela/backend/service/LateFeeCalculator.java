package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.LateFeePolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Responde "¿esta factura está en mora, y con qué recargo?" dado un
 * {@link LateFeePolicy} vigente, un {@code dueDate} y una fecha de referencia —
 * patrón Specification (táctico DDD, ver spec técnica): encapsula la regla de
 * cálculo de días/porcentaje sin ensuciar {@link InvoiceService}. Puro, sin
 * dependencias — no es un {@code @Service} de Spring.
 */
final class LateFeeCalculator {

    private LateFeeCalculator() {
    }

    static boolean isInArrears(LateFeePolicy policy, LocalDate dueDate, LocalDate fecha) {
        long diasDeAtraso = ChronoUnit.DAYS.between(dueDate, fecha);
        return diasDeAtraso > policy.getDiasDeGracia();
    }

    static boolean isAtCutoffRisk(LateFeePolicy policy, LocalDate dueDate, LocalDate fecha) {
        long diasDeAtraso = ChronoUnit.DAYS.between(dueDate, fecha);
        return diasDeAtraso >= policy.getDiasParaRiesgoDeCorte();
    }

    // Recargo = saldo * porcentaje * cantidad de períodos completos de diasPorPeriodo
    // transcurridos desde que se cumplieron los días de gracia — crece escalonado,
    // no de forma continua día a día.
    static BigDecimal calculateSurcharge(LateFeePolicy policy, LocalDate dueDate, LocalDate fecha, BigDecimal saldo) {
        if (!isInArrears(policy, dueDate, fecha)) {
            return BigDecimal.ZERO;
        }
        long diasEnMora = ChronoUnit.DAYS.between(dueDate, fecha) - policy.getDiasDeGracia();
        long periodosCompletos = diasEnMora / policy.getDiasPorPeriodo();
        if (periodosCompletos <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal porcentajeTotal = policy.getPorcentajeRecargoPorPeriodo().multiply(BigDecimal.valueOf(periodosCompletos));
        return saldo.multiply(porcentajeTotal).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
