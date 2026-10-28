package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.CourseTerms;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.domain.IssuanceMode;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-216")
@Tag("D-351")
class ConfigurationValuesChangeRowsTest {

    private static Map<String, String> tableOf(List<ChangeRow> rows) {
        Map<String, String> table = new LinkedHashMap<>();
        rows.forEach(row -> table.put(row.label(), row.today() + " | " + row.proposed() + " | " + row.difference()));
        return table;
    }

    private static Percentage points(String value) {
        return Percentage.ofPoints(new BigDecimal(value));
    }

    @Test
    void yearTermsShowEveryFieldInSpanishWithTheCurrentValueTheProposedOneAndTheDifference() {
        YearBillingTerms today = new YearBillingTerms(10, 2, 2, 7, 1, 10, 10, true, false, true, true, true, false);
        YearBillingTerms proposed = new YearBillingTerms(11, 1, 3, null, 2, 10, 15, false, true, true, false, true, true);

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildYearTermsRows(today, proposed));

        assertThat(table).hasSize(13);
        assertThat(table).containsEntry("Cantidad de cuotas del año", "10 cuotas | 11 cuotas | +1 cuota")
                .containsEntry("Cantidad de matrículas", "2 | 1 | -1")
                .containsEntry("Mes de la primera matrícula", "Febrero | Marzo | Cambia")
                .containsEntry("Mes de la segunda matrícula", "Julio | — | Se quita")
                .containsEntry("Anticipación de la matrícula", "1 mes | 2 meses | +1 mes")
                .containsEntry("Vencimiento de la matrícula tardía", "10 días | 10 días | Sin cambio")
                .containsEntry("Vencimiento de la cuota", "10 días | 15 días | +5 días")
                .containsEntry("Dar de baja a los suspendidos al cierre del ciclo", "Sí | No | Cambia")
                .containsEntry("Se paga durante la pausa", "No | Sí | Cambia")
                .containsEntry("Matrícula impaga bloquea la inscripción", "Sí | Sí | Sin cambio")
                .containsEntry("Matrícula impaga bloquea los exámenes", "Sí | No | Cambia")
                .containsEntry("Matrícula impaga bloquea los certificados", "Sí | Sí | Sin cambio")
                .containsEntry("Exigir matrícula a quien solo rinde finales", "No | Sí | Cambia");
    }

    @Test
    void yearTermsWithoutAnyCurrentValueShowEveryProposedFieldAsNew() {
        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildYearTermsRows(null, YearBillingTerms.initial(10)));

        assertThat(table).hasSize(13);
        assertThat(table).containsEntry("Cantidad de cuotas del año", "— | 10 cuotas | Nuevo").containsEntry("Mes de la primera matrícula", "— | Febrero | Nuevo")
                .containsEntry("Mes de la segunda matrícula", "— | — | Sin cambio").containsEntry("Se paga durante la pausa", "— | No | Nuevo");
    }

    @Test
    void cohortEntryShowsTheDiscountAsPercentageWithPercentagePointsDifference() {
        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildCohortEntryRows(new CohortEntryDiscount(points("10")), new CohortEntryDiscount(points("12.5"))));

        assertThat(table).containsOnlyKeys("Descuento de ingreso").containsEntry("Descuento de ingreso", "10 % | 12,5 % | +2,5 pp");
    }

    @Test
    void latePaymentShowsDaysCountsRateAndFlags() {
        LatePaymentTerms today = new LatePaymentTerms(3, points("0.5"), 30, 2, true, 10, true, false);
        LatePaymentTerms proposed = new LatePaymentTerms(5, points("0.35"), 30, 3, false, 7, true, true);

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildLatePaymentRows(today, proposed));

        assertThat(table).hasSize(8);
        assertThat(table).containsEntry("Cobro de recargos activo", "No | Sí | Cambia")
                .containsEntry("Días de gracia antes del recargo", "3 días | 5 días | +2 días")
                .containsEntry("Recargo diario", "0,5 % | 0,35 % | -0,15 pp")
                .containsEntry("Días de atraso para suspender", "30 días | 30 días | Sin cambio")
                .containsEntry("Cuotas vencidas para suspender", "2 cuotas | 3 cuotas | +1 cuota")
                .containsEntry("Bloquear inscripciones nuevas con deuda vencida", "Sí | No | Cambia")
                .containsEntry("Vencimiento de la nota de débito por recargo", "10 días | 7 días | -3 días")
                .containsEntry("Congelar el recargo al dar de baja", "Sí | Sí | Sin cambio");
    }

    @Test
    void issuanceTranslatesTheModeAndListsTheRetryWaits() {
        IssuanceSettings today = new IssuanceSettings(IssuanceMode.REVIEW, true, 3, List.of(1, 4));
        IssuanceSettings proposed = new IssuanceSettings(IssuanceMode.DIRECT, true, 5, List.of());

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildIssuanceRows(today, proposed));

        assertThat(table).hasSize(4);
        assertThat(table).containsEntry("Modo de emisión", "Con revisión previa | Emisión directa | Cambia")
                .containsEntry("Seguir emitiendo con deuda vencida", "Sí | Sí | Sin cambio")
                .containsEntry("Reintentos de emisión", "3 | 5 | +2")
                .containsEntry("Espera entre reintentos", "1 h, 4 h | Ninguno | Cambia");
    }

    @Test
    void allocationShowsTheAutomaticCreditApplicationFlag() {
        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildAllocationRows(new AllocationSettings(false), new AllocationSettings(true)));

        assertThat(table).containsOnlyKeys("Aplicar el saldo a favor automáticamente").containsEntry("Aplicar el saldo a favor automáticamente", "No | Sí | Cambia");
    }

    @Test
    void annualUpfrontShowsTheDeadlineDateWithDaysDifferenceAndTheHoursToPay() {
        AnnualUpfrontSettings today = new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 48);
        AnnualUpfrontSettings proposed = new AnnualUpfrontSettings(LocalDate.of(2027, 3, 15), 24);

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildAnnualUpfrontRows(today, proposed));

        assertThat(table).containsOnlyKeys("Fecha límite para pedir el pago anual", "Plazo para pagar")
                .containsEntry("Fecha límite para pedir el pago anual", "28/02/2027 | 15/03/2027 | +15 días")
                .containsEntry("Plazo para pagar", "48 horas | 24 horas | -24 horas");
    }

    @Test
    void courseTermsShowTheMonthlyInstallmentDueDays() {
        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildCourseTermsRows(new CourseTerms(10), new CourseTerms(1)));

        assertThat(table).containsOnlyKeys("Vencimiento de la cuota mensual").containsEntry("Vencimiento de la cuota mensual", "10 días | 1 día | -9 días");
    }

    @Test
    void irregularTermsListTheTypesAndFlagWhenTheyChange() {
        Map<String, String> changed = tableOf(ConfigurationValuesChangeRows.buildIrregularTermsRows(new IrregularTermTypes(List.of("Libre")), new IrregularTermTypes(List.of("Libre", "Oyente"))));
        Map<String, String> same = tableOf(ConfigurationValuesChangeRows.buildIrregularTermsRows(new IrregularTermTypes(List.of("Libre")), new IrregularTermTypes(List.of("Libre"))));

        assertThat(changed).containsOnlyKeys("Tipos de cursada irregular").containsEntry("Tipos de cursada irregular", "Libre | Libre, Oyente | Cambia");
        assertThat(same).containsEntry("Tipos de cursada irregular", "Libre | Libre | Sin cambio");
    }

    @Test
    void dashboardGoalsShowPercentagesAndShowAnUnsetGoalAsAbsent() {
        BillingDashboardGoals today = new BillingDashboardGoals(points("85"), null, null, null);
        BillingDashboardGoals proposed = new BillingDashboardGoals(points("90"), points("12"), 3, null);

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildDashboardGoalsRows(today, proposed));

        assertThat(table).hasSize(4);
        assertThat(table).containsEntry("Pago en término, mínimo", "85 % | 90 % | +5 pp").containsEntry("Deuda vencida, máximo", "— | 12 % | Nuevo")
                .containsEntry("Revisión de comprobantes, máximo", "— | 3 días hábiles | Nuevo").containsEntry("Avisos pagados en 72 horas, mínimo", "— | — | Sin cambio");
    }

    @Test
    void collectionReminderSettingsShowTheEmailWindowTheCapAndTheThresholds() {
        CollectionReminderSettings today = new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 250, 14, 2, true);
        CollectionReminderSettings proposed = new CollectionReminderSettings(false, 1, LocalTime.of(10, 0), LocalTime.of(11, 0), 280, 14, 3, false);

        Map<String, String> table = tableOf(ConfigurationValuesChangeRows.buildCollectionReminderSettingsRows(today, proposed));

        assertThat(table).hasSize(8);
        assertThat(table).containsEntry("Aviso al suspenderse", "Sí | No | Cambia")
                .containsEntry("Meses de resumen posterior a la baja", "6 meses | 1 mes | -5 meses")
                .containsEntry("Envío de mails desde", "09:00 | 10:00 | Cambia")
                .containsEntry("Envío de mails hasta", "11:00 | 11:00 | Sin cambio")
                .containsEntry("Tope diario de mails", "250 mails | 280 mails | +30 mails")
                .containsEntry("Días de atraso para «Para contactar»", "14 días | 14 días | Sin cambio")
                .containsEntry("Cuotas vencidas para «Para contactar»", "2 cuotas | 3 cuotas | +1 cuota")
                .containsEntry("Resumen diario activo", "Sí | No | Cambia");
    }
}
