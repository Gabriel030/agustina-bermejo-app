package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Discount;
import com.red_escuela.backend.entity.DiscountType;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.Promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calcula el monto final de una cuota/matrícula a partir del precio base más los
 * descuentos individuales y promociones segmentadas vigentes — puro (sin estado,
 * sin I/O), no es un {@code @Service} de Spring porque no tiene dependencias que
 * inyectar. Patrón Strategy (ver spec técnica de Épica 8 Fase 1): el "cómo se
 * combinan los descuentos" vive acá, aislado de {@link InvoiceService}, que solo
 * le pasa el precio base y las listas ya resueltas.
 *
 * Regla de negocio: un {@link DiscountType#BECA_TOTAL} (solo posible en
 * {@link Discount} individual, nunca en {@link Promotion}) hace el monto cero sin
 * importar el resto — cualquier otro descuento/promoción se suma linealmente
 * (porcentajes sobre el precio base, montos fijos restados directo), sin tope ni
 * prioridad entre ellos (decisión de negocio explícita).
 */
final class PricingCalculator {

    private PricingCalculator() {
    }

    static BigDecimal calcularMontoFinal(BigDecimal precioBase, List<Discount> descuentos, List<Promotion> promociones) {
        boolean tieneBecaTotal = descuentos.stream().anyMatch(d -> d.getTipo() == DiscountType.BECA_TOTAL);
        if (tieneBecaTotal) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalPorcentaje = descuentos.stream()
                .filter(d -> d.getTipo() == DiscountType.PORCENTAJE)
                .map(Discount::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(promociones.stream()
                        .filter(p -> p.getTipo() == DiscountType.PORCENTAJE)
                        .map(Promotion::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal totalMontoFijo = descuentos.stream()
                .filter(d -> d.getTipo() == DiscountType.MONTO_FIJO)
                .map(Discount::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(promociones.stream()
                        .filter(p -> p.getTipo() == DiscountType.MONTO_FIJO)
                        .map(Promotion::getValor)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal descuentoPorPorcentaje = precioBase
                .multiply(totalPorcentaje)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal montoFinal = precioBase.subtract(descuentoPorPorcentaje).subtract(totalMontoFijo);
        return montoFinal.max(BigDecimal.ZERO);
    }

    /**
     * Épica 8 Fase 2: la matrícula de la segunda cohorte del año (quien entra a
     * mitad de año lectivo) cuesta la mitad de la primera — prorrateo automático,
     * no un {@code FeeSchedule} separado por cohorte (evita que alguien actualice
     * el precio de la carrera y se olvide de actualizar también el de la segunda
     * cohorte). Se aplica ANTES de descuentos/promociones (mismo orden que hoy:
     * esos se calculan sobre el monto ya prorrateado).
     */
    static BigDecimal calcularMontoMatricula(BigDecimal montoBase, ProgramCohort cohort) {
        if (cohort.getOrdenEnAnio() == 2) {
            return montoBase.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        }
        return montoBase;
    }
}
