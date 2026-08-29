package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Discount;
import com.red_escuela.backend.entity.DiscountType;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.Promotion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Puro (sin Spring, sin base de datos) — PricingCalculator no tiene dependencias
 * que inyectar, forzar un contexto de Spring acá sería ceremonia sin beneficio
 * real (a diferencia del resto de los tests del proyecto, que sí necesitan
 * Postgres real). Cubre la regla de negocio "se suman, salvo BECA_TOTAL que
 * gana sobre todo".
 */
class PricingCalculatorTest {

    private Discount descuento(DiscountType tipo, BigDecimal valor) {
        Discount discount = new Discount();
        discount.setTipo(tipo);
        discount.setValor(valor);
        return discount;
    }

    private Promotion promocion(DiscountType tipo, BigDecimal valor) {
        Promotion promotion = new Promotion();
        promotion.setTipo(tipo);
        promotion.setValor(valor);
        return promotion;
    }

    @Test
    void sinDescuentosNiPromocionesDevuelveElPrecioBase() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(BigDecimal.valueOf(10000), List.of(), List.of());
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void descuentoIndividualPorcentajeSeAplicaSobreElPrecioBase() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(
                BigDecimal.valueOf(10000), List.of(descuento(DiscountType.PORCENTAJE, BigDecimal.valueOf(20))), List.of());
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(8000));
    }

    @Test
    void descuentoIndividualYPromocionSegmentadaSeSuman() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(
                BigDecimal.valueOf(10000),
                List.of(descuento(DiscountType.PORCENTAJE, BigDecimal.valueOf(20))),
                List.of(promocion(DiscountType.PORCENTAJE, BigDecimal.valueOf(10))));
        // 20% + 10% = 30% de descuento total → 7000
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(7000));
    }

    @Test
    void montoFijoSeRestaDirectoDelPrecioBase() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(
                BigDecimal.valueOf(10000), List.of(descuento(DiscountType.MONTO_FIJO, BigDecimal.valueOf(3000))), List.of());
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(7000));
    }

    @Test
    void becaTotalDaCeroSinImportarOtrosDescuentos() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(
                BigDecimal.valueOf(10000),
                List.of(descuento(DiscountType.BECA_TOTAL, BigDecimal.ZERO)),
                List.of(promocion(DiscountType.PORCENTAJE, BigDecimal.valueOf(10))));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void descuentosQueSuperanElPrecioBaseNuncaDanNegativo() {
        BigDecimal resultado = PricingCalculator.calcularMontoFinal(
                BigDecimal.valueOf(1000), List.of(descuento(DiscountType.MONTO_FIJO, BigDecimal.valueOf(5000))), List.of());
        assertThat(resultado).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ProgramCohort cohorte(int ordenEnAnio) {
        ProgramCohort cohort = new ProgramCohort();
        cohort.setOrdenEnAnio(ordenEnAnio);
        return cohort;
    }

    @Test
    void matriculaDeLaPrimeraCohorteDelAnioEsElMontoCompleto() {
        BigDecimal resultado = PricingCalculator.calcularMontoMatricula(BigDecimal.valueOf(50000), cohorte(1));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }

    @Test
    void matriculaDeLaSegundaCohorteDelAnioEsLaMitad() {
        BigDecimal resultado = PricingCalculator.calcularMontoMatricula(BigDecimal.valueOf(50000), cohorte(2));
        assertThat(resultado).isEqualByComparingTo(BigDecimal.valueOf(25000));
    }
}
