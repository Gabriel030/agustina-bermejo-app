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

    static boolean isInArrears(LateFeePolicy policy, LocalDate dueDate, LocalDate date) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, date);
        return daysLate > policy.getGracePeriodDays();
    }

    static boolean isAtCutoffRisk(LateFeePolicy policy, LocalDate dueDate, LocalDate date) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, date);
        return daysLate >= policy.getDaysUntilCutoffRisk();
    }

    // Recargo = saldo * porcentaje * cantidad de períodos completos de diasPorPeriodo
    // transcurridos desde que se cumplieron los días de gracia — crece escalonado,
    // no de forma continua día a día.
    static BigDecimal calculateSurcharge(LateFeePolicy policy, LocalDate dueDate, LocalDate date, BigDecimal balance) {
        if (!isInArrears(policy, dueDate, date)) {
            return BigDecimal.ZERO;
        }
        long daysInArrears = ChronoUnit.DAYS.between(dueDate, date) - policy.getGracePeriodDays();
        long fullPeriods = daysInArrears / policy.getPeriodDays();
        if (fullPeriods <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalPercentage = policy.getSurchargePercentagePerPeriod().multiply(BigDecimal.valueOf(fullPeriods));
        return balance.multiply(totalPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
