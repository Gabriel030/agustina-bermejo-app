package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;
import com.red_escuela.backend.billing.configuration.domain.BillingDashboardGoals;
import com.red_escuela.backend.billing.configuration.domain.CohortEntryDiscount;
import com.red_escuela.backend.billing.configuration.domain.CollectionReminderSettings;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationValues;
import com.red_escuela.backend.billing.configuration.domain.CourseReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.CourseTerms;
import com.red_escuela.backend.billing.configuration.domain.IrregularTermTypes;
import com.red_escuela.backend.billing.configuration.domain.IssuanceMode;
import com.red_escuela.backend.billing.configuration.domain.IssuanceSettings;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.ProgramReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleProposal;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleResolver;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleTarget;
import com.red_escuela.backend.billing.configuration.domain.ReminderScheduleVersion;
import com.red_escuela.backend.billing.configuration.domain.ReminderSendingOverrides;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import com.red_escuela.backend.billing.configuration.domain.TeachingGroup;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.ChangeRow;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-216")
@Tag("D-351")
class BillingConfigurationChangeRowsAssemblerTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 3);
    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2027, 4, 1);

    private final BillingConfigurationReader reader = mock(BillingConfigurationReader.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final BillingConfigurationChangeRowsAssembler assembler = new BillingConfigurationChangeRowsAssembler(reader, clock);

    private static ChangeBillingConfigurationCommand commandOf(ConfigurationTopic topic, ConfigurationValues values) {
        return new ChangeBillingConfigurationCommand(topic, values, EFFECTIVE_FROM, "motivo", 7L, false);
    }

    private static Percentage points(String value) {
        return Percentage.ofPoints(new BigDecimal(value));
    }

    private static ChangeRow rowLabeled(List<ChangeRow> rows, String label) {
        return rows.stream().filter(row -> row.label().equals(label)).findFirst().orElseThrow();
    }

    @Test
    void yearTermsReadTheValuesInForceTodayOfTheProgramAndTerm() {
        when(reader.findYearTermsEffectiveOn(4L, 9L, TODAY)).thenReturn(Optional.of(YearBillingTerms.initial(10)));
        YearBillingTerms proposed = new YearBillingTerms(11, 1, 2, null, 1, 10, 10, true, false, true, true, true, false);

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofYearTerms(4L, 9L), proposed));

        assertThat(rows).hasSize(13);
        assertThat(rowLabeled(rows, "Cantidad de cuotas del año")).isEqualTo(new ChangeRow("Cantidad de cuotas del año", "10 cuotas", "11 cuotas", "+1 cuota"));
    }

    @Test
    void yearTermsWithNothingInForceShowTheTodayColumnEmpty() {
        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofYearTerms(4L, 9L), YearBillingTerms.initial(10)));

        assertThat(rowLabeled(rows, "Cantidad de cuotas del año")).isEqualTo(new ChangeRow("Cantidad de cuotas del año", "—", "10 cuotas", "Nuevo"));
    }

    @Test
    void cohortEntryReadsTheDiscountOfTheCohortAndShowsZeroWhenThereIsNone() {
        when(reader.cohortEntryDiscountEffectiveOn(12L, TODAY)).thenReturn(points("10"));
        when(reader.cohortEntryDiscountEffectiveOn(13L, TODAY)).thenReturn(Percentage.ofFraction(BigDecimal.ZERO));

        List<ChangeRow> configured = assembler.assembleRows(commandOf(ConfigurationTopic.ofCohortEntry(12L), new CohortEntryDiscount(points("15"))));
        List<ChangeRow> unconfigured = assembler.assembleRows(commandOf(ConfigurationTopic.ofCohortEntry(13L), new CohortEntryDiscount(points("15"))));

        assertThat(configured).containsExactly(new ChangeRow("Descuento de ingreso", "10 %", "15 %", "+5 pp"));
        assertThat(unconfigured).containsExactly(new ChangeRow("Descuento de ingreso", "0 %", "15 %", "+15 pp"));
    }

    @Test
    void latePaymentReadsTheGlobalSettingsInForceToday() {
        when(reader.findLatePaymentSettingsEffectiveOn(TODAY)).thenReturn(Optional.of(new LatePaymentTerms(3, points("0.5"), 30, 2, true, 10, true, false)));
        LatePaymentTerms proposed = new LatePaymentTerms(5, points("0.5"), 30, 2, true, 10, true, true);

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.LATE_PAYMENT), proposed));

        assertThat(rows).hasSize(8);
        assertThat(rowLabeled(rows, "Días de gracia antes del recargo")).isEqualTo(new ChangeRow("Días de gracia antes del recargo", "3 días", "5 días", "+2 días"));
        assertThat(rowLabeled(rows, "Cobro de recargos activo")).isEqualTo(new ChangeRow("Cobro de recargos activo", "No", "Sí", "Cambia"));
    }

    @Test
    void issuanceReadsTheGlobalSettingsInForceToday() {
        when(reader.findIssuanceSettingsEffectiveOn(TODAY)).thenReturn(Optional.of(new IssuanceSettings(IssuanceMode.REVIEW, false, 3, List.of(1))));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.ISSUANCE),
                new IssuanceSettings(IssuanceMode.DIRECT, false, 3, List.of(1))));

        assertThat(rows).hasSize(4);
        assertThat(rowLabeled(rows, "Modo de emisión")).isEqualTo(new ChangeRow("Modo de emisión", "Con revisión previa", "Emisión directa", "Cambia"));
    }

    @Test
    void allocationReadsTheGlobalSettingsInForceToday() {
        when(reader.findAllocationSettingsEffectiveOn(TODAY)).thenReturn(Optional.of(new AllocationSettings(false)));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.ALLOCATION), new AllocationSettings(true)));

        assertThat(rows).containsExactly(new ChangeRow("Aplicar el saldo a favor automáticamente", "No", "Sí", "Cambia"));
    }

    @Test
    void annualUpfrontReadsTheSettingsOfTheProgramAndTerm() {
        when(reader.findAnnualUpfrontSettingsEffectiveOn(4L, 9L, TODAY)).thenReturn(Optional.of(new AnnualUpfrontSettings(LocalDate.of(2027, 2, 28), 48)));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofAnnualUpfront(4L, 9L), new AnnualUpfrontSettings(LocalDate.of(2027, 3, 31), 48)));

        assertThat(rows).containsExactly(new ChangeRow("Fecha límite para pedir el pago anual", "28/02/2027", "31/03/2027", "+31 días"),
                new ChangeRow("Plazo para pagar", "48 horas", "48 horas", "Sin cambio"));
    }

    @Test
    void courseTermsReadTheTermsOfTheCourse() {
        when(reader.courseTermsEffectiveOn(21L, TODAY)).thenReturn(new CourseTerms(10));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofCourseTerms(21L), new CourseTerms(15)));

        assertThat(rows).containsExactly(new ChangeRow("Vencimiento de la cuota mensual", "10 días", "15 días", "+5 días"));
    }

    @Test
    void irregularTermsReadTheTypesInForceToday() {
        when(reader.irregularTermTypesEffectiveOn(TODAY)).thenReturn(new IrregularTermTypes(List.of(TermType.ANNUAL)));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.IRREGULAR_TERMS), new IrregularTermTypes(List.of(TermType.ANNUAL, TermType.INTENSIVE))));

        assertThat(rows).containsExactly(new ChangeRow("Tipos de cursada irregular", "Anual", "Anual, Intensivo de verano", "Cambia"));
    }

    @Test
    void dashboardGoalsReadTheGoalsInForceToday() {
        when(reader.billingDashboardGoalsEffectiveOn(TODAY)).thenReturn(BillingDashboardGoals.initial());

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.BILLING_DASHBOARD_GOALS),
                new BillingDashboardGoals(points("90"), null, null, null)));

        assertThat(rows).hasSize(4);
        assertThat(rowLabeled(rows, "Pago en término, mínimo")).isEqualTo(new ChangeRow("Pago en término, mínimo", "85 %", "90 %", "+5 pp"));
    }

    @Test
    void collectionRemindersReadTheSettingsInForceToday() {
        when(reader.collectionReminderSettingsEffectiveOn(TODAY)).thenReturn(CollectionReminderSettings.initial());
        CollectionReminderSettings proposed = new CollectionReminderSettings(true, 6, LocalTime.of(9, 0), LocalTime.of(11, 0), 280, 14, 2, true);

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofGlobal(ConfigurationScope.COLLECTION_REMINDERS), proposed));

        assertThat(rows).hasSize(8);
        assertThat(rowLabeled(rows, "Tope diario de mails")).isEqualTo(new ChangeRow("Tope diario de mails", "250 mails", "280 mails", "+30 mails"));
    }

    private static ResolvedReminderSchedule resolvedWith(ReminderSchedule schedule) {
        return ReminderScheduleResolver.resolve(Optional.empty(), Optional.of(new ReminderScheduleVersion(1L, schedule)), CollectionReminderSettings.initial(), null);
    }

    private static ReminderSchedule scheduleWithBeforeDue(int offset) {
        return new ReminderSchedule(false, List.of(ReminderNotice.of("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, offset,
                EnumSet.allOf(ReminderChannel.class))), ReminderSendingOverrides.none());
    }

    @Test
    void programReminderScheduleComparesTheResolvedScheduleOfTheProgramAgainstTheProposalAtTheSameDate() {
        ReminderScheduleTarget target = new ReminderScheduleTarget(TeachingGroup.PROGRAMS, 5L);
        ReminderSchedule proposed = scheduleWithBeforeDue(-3);
        when(reader.reminderScheduleFor(target, TODAY)).thenReturn(resolvedWith(scheduleWithBeforeDue(-1)));
        when(reader.reminderScheduleWithProposal(target, TODAY, new ReminderScheduleProposal(target, proposed, null))).thenReturn(resolvedWith(proposed));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.PROGRAMS, 5L), new ProgramReminderSchedule(proposed)));

        assertThat(rows).containsExactly(new ChangeRow("Recordatorio previo al vencimiento", "1 día antes del vencimiento · App y Mail", "3 días antes del vencimiento · App y Mail", "Cambia"));
    }

    @Test
    void courseReminderScheduleOfTheGroupUsesTheGroupTargetWithoutAnId() {
        ReminderScheduleTarget target = new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, null);
        ReminderSchedule proposed = scheduleWithBeforeDue(-2);
        when(reader.reminderScheduleFor(target, TODAY)).thenReturn(resolvedWith(scheduleWithBeforeDue(-1)));
        when(reader.reminderScheduleWithProposal(target, TODAY, new ReminderScheduleProposal(target, proposed, null))).thenReturn(resolvedWith(proposed));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofReminderScheduleGroup(TeachingGroup.MONTHLY_COURSES), new CourseReminderSchedule(proposed)));

        assertThat(rows).containsExactly(new ChangeRow("Recordatorio previo al vencimiento", "1 día antes del vencimiento · App y Mail", "2 días antes del vencimiento · App y Mail", "Cambia"));
    }

    @Test
    void courseReminderScheduleOfACourseUsesTheCourseAsTarget() {
        ReminderScheduleTarget target = new ReminderScheduleTarget(TeachingGroup.MONTHLY_COURSES, 8L);
        ReminderSchedule proposed = scheduleWithBeforeDue(-2);
        when(reader.reminderScheduleFor(target, TODAY)).thenReturn(resolvedWith(scheduleWithBeforeDue(-1)));
        when(reader.reminderScheduleWithProposal(target, TODAY, new ReminderScheduleProposal(target, proposed, null))).thenReturn(resolvedWith(proposed));

        List<ChangeRow> rows = assembler.assembleRows(commandOf(ConfigurationTopic.ofReminderScheduleException(TeachingGroup.MONTHLY_COURSES, 8L), new CourseReminderSchedule(proposed)));

        assertThat(rows).hasSize(1);
    }
}
