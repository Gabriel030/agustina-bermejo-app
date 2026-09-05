package com.red_escuela.backend.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Calcula qué número de cuota (si alguno) corresponde a una fecha dada, dentro
 * de un plan de pagos de {@code totalCuotas} cuotas mensuales consecutivas
 * arrancando en {@code cursadaStart} — puro (sin estado, sin I/O), mismo
 * criterio que {@link PricingCalculator}/{@link LateFeeCalculator} (Épica 8
 * Fase 1): sin dependencias de Spring, no es un {@code @Service}.
 *
 * Asunción explícita a revisar si no coincide con la realidad del calendario
 * académico: las cuotas son mensuales y CONSECUTIVAS desde {@code cursadaStart}
 * (cuota 1 el mes de inicio, cuota 2 el siguiente, etc.), sin modelar una pausa
 * de verano entre años lectivos — no fue parte de lo discutido con el usuario
 * al especificar esta fase. Para una carrera de 3 años × 10 cuotas/año con
 * cursada arrancando en marzo, esto da cuotas consecutivas marzo-diciembre
 * (10 meses, encaja con un año lectivo real); para una cohorte de agosto, las
 * 10 cuotas de su primer año corren agosto→mayo del año siguiente, sin
 * alinearse con el calendario marzo-diciembre de la cohorte 1 — si el negocio
 * necesita que sí se alineen (con pausa dic-feb), hay que revisar este cálculo
 * antes de dar por buena esta fase.
 */
final class InstallmentScheduleCalculator {

    private InstallmentScheduleCalculator() {
    }

    /**
     * @return el número de cuota (1-based) que corresponde a {@code fecha}, o
     * {@code null} si {@code fecha} es anterior al inicio de cursada o el plan
     * ya se agotó (no hay cuota "de más" una vez completado {@code totalCuotas}).
     */
    static Integer calculateInstallmentNumber(LocalDate courseStart, int totalInstallments, LocalDate date) {
        if (date.isBefore(courseStart)) {
            return null;
        }
        long monthsElapsed = ChronoUnit.MONTHS.between(
                courseStart.withDayOfMonth(1), date.withDayOfMonth(1));
        int installmentNumber = (int) monthsElapsed + 1;
        return installmentNumber > totalInstallments ? null : installmentNumber;
    }
}
