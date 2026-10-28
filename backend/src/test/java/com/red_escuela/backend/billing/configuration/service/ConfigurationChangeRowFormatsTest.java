package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-216")
@Tag("D-351")
class ConfigurationChangeRowFormatsTest {

    @Test
    void countsUseTheSingularOnlyForOneAndSignTheDifference() {
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", 1, 2, "día", "días")).isEqualTo(new ChangeRow("Plazo", "1 día", "2 días", "+1 día"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", 3, 2, "día", "días")).isEqualTo(new ChangeRow("Plazo", "3 días", "2 días", "-1 día"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", 3, 3, "día", "días")).isEqualTo(new ChangeRow("Plazo", "3 días", "3 días", "Sin cambio"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", 0, 5, "día", "días")).isEqualTo(new ChangeRow("Plazo", "0 días", "5 días", "+5 días"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Cantidad", 3, 1, null, null)).isEqualTo(new ChangeRow("Cantidad", "3", "1", "-2"));
    }

    @Test
    void aValueThatAppearsOrDisappearsIsFlaggedInsteadOfComputingADifference() {
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", null, 2, "día", "días")).isEqualTo(new ChangeRow("Plazo", "—", "2 días", "Nuevo"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", 2, null, "día", "días")).isEqualTo(new ChangeRow("Plazo", "2 días", "—", "Se quita"));
        assertThat(ConfigurationChangeRowFormats.buildCountRow("Plazo", null, null, "día", "días")).isEqualTo(new ChangeRow("Plazo", "—", "—", "Sin cambio"));
    }

    @Test
    void percentagesKeepTheirDecimalsWithACommaAndDifferInPercentagePoints() {
        Percentage low = Percentage.ofPoints(new BigDecimal("0.033"));
        Percentage high = Percentage.ofPoints(new BigDecimal("10"));

        assertThat(ConfigurationChangeRowFormats.buildPercentageRow("Tasa", low, high)).isEqualTo(new ChangeRow("Tasa", "0,033 %", "10 %", "+9,967 pp"));
        assertThat(ConfigurationChangeRowFormats.buildPercentageRow("Tasa", high, high)).isEqualTo(new ChangeRow("Tasa", "10 %", "10 %", "Sin cambio"));
        assertThat(ConfigurationChangeRowFormats.buildPercentageRow("Tasa", Percentage.ofFraction(BigDecimal.ZERO), low)).isEqualTo(new ChangeRow("Tasa", "0 %", "0,033 %", "+0,033 pp"));
        assertThat(ConfigurationChangeRowFormats.buildPercentageRow("Tasa", null, high)).isEqualTo(new ChangeRow("Tasa", "—", "10 %", "Nuevo"));
    }

    @Test
    void datesShowTheDayDifferenceWithSingularAndPlural() {
        LocalDate base = LocalDate.of(2027, 3, 1);

        assertThat(ConfigurationChangeRowFormats.buildDateRow("Fecha", base, base.plusDays(1))).isEqualTo(new ChangeRow("Fecha", "01/03/2027", "02/03/2027", "+1 día"));
        assertThat(ConfigurationChangeRowFormats.buildDateRow("Fecha", base, base.minusDays(2))).isEqualTo(new ChangeRow("Fecha", "01/03/2027", "27/02/2027", "-2 días"));
        assertThat(ConfigurationChangeRowFormats.buildDateRow("Fecha", base, base)).isEqualTo(new ChangeRow("Fecha", "01/03/2027", "01/03/2027", "Sin cambio"));
        assertThat(ConfigurationChangeRowFormats.buildDateRow("Fecha", null, base)).isEqualTo(new ChangeRow("Fecha", "—", "01/03/2027", "Nuevo"));
    }

    @Test
    void flagsTimesMonthsAndTextsReportOnlyWhetherTheyChange() {
        assertThat(ConfigurationChangeRowFormats.buildFlagRow("Activo", true, false)).isEqualTo(new ChangeRow("Activo", "Sí", "No", "Cambia"));
        assertThat(ConfigurationChangeRowFormats.buildFlagRow("Activo", true, true)).isEqualTo(new ChangeRow("Activo", "Sí", "Sí", "Sin cambio"));
        assertThat(ConfigurationChangeRowFormats.buildTimeRow("Hora", LocalTime.of(9, 5), LocalTime.of(11, 0))).isEqualTo(new ChangeRow("Hora", "09:05", "11:00", "Cambia"));
        assertThat(ConfigurationChangeRowFormats.buildMonthRow("Mes", 9, 12)).isEqualTo(new ChangeRow("Mes", "Septiembre", "Diciembre", "Cambia"));
        assertThat(ConfigurationChangeRowFormats.buildMonthRow("Mes", 1, 1)).isEqualTo(new ChangeRow("Mes", "Enero", "Enero", "Sin cambio"));
        assertThat(ConfigurationChangeRowFormats.buildTextRow("Modo", "A", "B")).isEqualTo(new ChangeRow("Modo", "A", "B", "Cambia"));
        assertThat(ConfigurationChangeRowFormats.buildListRow("Lista", List.of("a", "b"), List.of())).isEqualTo(new ChangeRow("Lista", "a, b", "Ninguno", "Cambia"));
        assertThat(ConfigurationChangeRowFormats.buildListRow("Lista", null, List.of("a"))).isEqualTo(new ChangeRow("Lista", "—", "a", "Nuevo"));
    }
}
