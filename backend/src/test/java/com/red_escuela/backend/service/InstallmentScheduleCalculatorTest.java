package com.red_escuela.backend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentScheduleCalculatorTest {

    @Test
    void antesDeLaCursadaNoCorrespondeNingunaCuota() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 10, LocalDate.of(2026, 2, 15)))
                .isNull();
    }

    @Test
    void elMesDeInicioEsLaCuota1() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 10, LocalDate.of(2026, 3, 15)))
                .isEqualTo(1);
    }

    @Test
    void unPlanDe10CuotasDesdeMarzoRecorreCorrectamenteHastaDiciembre() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        int[] esperado = IntStream.rangeClosed(1, 10).toArray();
        for (int mes = 0; mes < 10; mes++) {
            LocalDate fecha = cursadaStart.plusMonths(mes);
            assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 10, fecha))
                    .as("mes " + mes)
                    .isEqualTo(esperado[mes]);
        }
    }

    @Test
    void unaVezAgotadoElPlanNoDevuelveMasCuotas() {
        LocalDate cursadaStart = LocalDate.of(2026, 3, 1);
        assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 10, LocalDate.of(2027, 1, 1)))
                .isNull();
    }

    @Test
    void unCursoDeTresCuotasDaNullEnLaCuartaMensualidad() {
        LocalDate cursadaStart = LocalDate.of(2026, 5, 1);
        assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 3, LocalDate.of(2026, 7, 20)))
                .isEqualTo(3);
        assertThat(InstallmentScheduleCalculator.calcularNumeroDeCuota(cursadaStart, 3, LocalDate.of(2026, 8, 1)))
                .isNull();
    }
}
