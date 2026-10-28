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

import java.util.List;
import java.util.function.Function;

import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildCountRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildDateRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildFlagRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildListRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildMonthRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildPercentageRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildTextRow;
import static com.red_escuela.backend.billing.configuration.service.ConfigurationChangeRowFormats.buildTimeRow;

final class ConfigurationValuesChangeRows {
    private static final String DAY = "día";
    private static final String DAYS = "días";

    private ConfigurationValuesChangeRows() {
    }

    static List<ChangeRow> buildYearTermsRows(YearBillingTerms today, YearBillingTerms proposed) {
        return List.of(
                buildCountRow("Cantidad de cuotas del año", read(today, YearBillingTerms::recurringFeeInvoicesCount), proposed.recurringFeeInvoicesCount(), "cuota", "cuotas"),
                buildCountRow("Cantidad de matrículas", read(today, YearBillingTerms::enrollmentFeeCount), proposed.enrollmentFeeCount(), null, null),
                buildMonthRow("Mes de la primera matrícula", read(today, YearBillingTerms::firstEnrollmentFeeMonth), proposed.firstEnrollmentFeeMonth()),
                buildMonthRow("Mes de la segunda matrícula", read(today, YearBillingTerms::secondEnrollmentFeeMonth), proposed.secondEnrollmentFeeMonth()),
                buildCountRow("Anticipación de la matrícula", read(today, YearBillingTerms::enrollmentFeeLeadMonths), proposed.enrollmentFeeLeadMonths(), "mes", "meses"),
                buildCountRow("Vencimiento de la matrícula tardía", read(today, YearBillingTerms::lateEnrollmentFeeDueDays), proposed.lateEnrollmentFeeDueDays(), DAY, DAYS),
                buildCountRow("Vencimiento de la cuota", read(today, YearBillingTerms::recurringFeeDueDays), proposed.recurringFeeDueDays(), DAY, DAYS),
                buildFlagRow("Dar de baja a los suspendidos al cierre del ciclo", read(today, YearBillingTerms::withdrawSuspendedAtTermEnd), proposed.withdrawSuspendedAtTermEnd()),
                buildFlagRow("Se paga durante la pausa", read(today, YearBillingTerms::paysWhilePaused), proposed.paysWhilePaused()),
                buildFlagRow("Matrícula impaga bloquea la inscripción", read(today, YearBillingTerms::blocksEnrollment), proposed.blocksEnrollment()),
                buildFlagRow("Matrícula impaga bloquea los exámenes", read(today, YearBillingTerms::blocksExamRegistration), proposed.blocksExamRegistration()),
                buildFlagRow("Matrícula impaga bloquea los certificados", read(today, YearBillingTerms::blocksCertificates), proposed.blocksCertificates()),
                buildFlagRow("Exigir matrícula a quien solo rinde finales", read(today, YearBillingTerms::requireEnrollmentFeeForFinalExamOnlyStudents),
                        proposed.requireEnrollmentFeeForFinalExamOnlyStudents()));
    }

    static List<ChangeRow> buildCohortEntryRows(CohortEntryDiscount today, CohortEntryDiscount proposed) {
        return List.of(buildPercentageRow("Descuento de ingreso", read(today, CohortEntryDiscount::discount), proposed.discount()));
    }

    static List<ChangeRow> buildLatePaymentRows(LatePaymentTerms today, LatePaymentTerms proposed) {
        return List.of(
                buildFlagRow("Cobro de recargos activo", read(today, LatePaymentTerms::lateFeeChargingEnabled), proposed.lateFeeChargingEnabled()),
                buildCountRow("Días de gracia antes del recargo", read(today, LatePaymentTerms::gracePeriodDays), proposed.gracePeriodDays(), DAY, DAYS),
                buildPercentageRow("Recargo diario", read(today, LatePaymentTerms::dailyLateFeeRate), proposed.dailyLateFeeRate()),
                buildCountRow("Días de atraso para suspender", read(today, LatePaymentTerms::suspensionAfterDays), proposed.suspensionAfterDays(), DAY, DAYS),
                buildCountRow("Cuotas vencidas para suspender", read(today, LatePaymentTerms::suspensionAfterInvoiceCount), proposed.suspensionAfterInvoiceCount(), "cuota", "cuotas"),
                buildFlagRow("Bloquear inscripciones nuevas con deuda vencida", read(today, LatePaymentTerms::blockNewEnrollmentsWhenOverdue),
                        proposed.blockNewEnrollmentsWhenOverdue()),
                buildCountRow("Vencimiento de la nota de débito por recargo", read(today, LatePaymentTerms::lateFeeDebitNoteDueDays), proposed.lateFeeDebitNoteDueDays(), DAY, DAYS),
                buildFlagRow("Congelar el recargo al dar de baja", read(today, LatePaymentTerms::freezeLateFeesOnWithdrawal), proposed.freezeLateFeesOnWithdrawal()));
    }

    static List<ChangeRow> buildIssuanceRows(IssuanceSettings today, IssuanceSettings proposed) {
        return List.of(
                buildTextRow("Modo de emisión", issuanceModeText(read(today, IssuanceSettings::issuanceMode)), issuanceModeText(proposed.issuanceMode())),
                buildFlagRow("Seguir emitiendo con deuda vencida", read(today, IssuanceSettings::continueIssuingWhileOverdue), proposed.continueIssuingWhileOverdue()),
                buildCountRow("Reintentos de emisión", read(today, IssuanceSettings::maxRetryAttempts), proposed.maxRetryAttempts(), null, null),
                buildListRow("Espera entre reintentos", hoursOf(read(today, IssuanceSettings::retryBackoffHours)), hoursOf(proposed.retryBackoffHours())));
    }

    static List<ChangeRow> buildAllocationRows(AllocationSettings today, AllocationSettings proposed) {
        return List.of(buildFlagRow("Aplicar el saldo a favor automáticamente", read(today, AllocationSettings::autoApplyCreditBalance), proposed.autoApplyCreditBalance()));
    }

    static List<ChangeRow> buildAnnualUpfrontRows(AnnualUpfrontSettings today, AnnualUpfrontSettings proposed) {
        return List.of(
                buildDateRow("Fecha límite para pedir el pago anual", read(today, AnnualUpfrontSettings::requestDeadline), proposed.requestDeadline()),
                buildCountRow("Plazo para pagar", read(today, AnnualUpfrontSettings::paymentDeadlineHours), proposed.paymentDeadlineHours(), "hora", "horas"));
    }

    static List<ChangeRow> buildCourseTermsRows(CourseTerms today, CourseTerms proposed) {
        return List.of(buildCountRow("Vencimiento de la cuota mensual", read(today, CourseTerms::monthlyInstallmentDueDays), proposed.monthlyInstallmentDueDays(), DAY, DAYS));
    }

    static List<ChangeRow> buildIrregularTermsRows(IrregularTermTypes today, IrregularTermTypes proposed) {
        return List.of(buildListRow("Tipos de cursada irregular", read(today, IrregularTermTypes::types), proposed.types()));
    }

    static List<ChangeRow> buildDashboardGoalsRows(BillingDashboardGoals today, BillingDashboardGoals proposed) {
        return List.of(
                buildPercentageRow("Pago en término, mínimo", read(today, BillingDashboardGoals::onTimePaymentAtLeast), proposed.onTimePaymentAtLeast()),
                buildPercentageRow("Deuda vencida, máximo", read(today, BillingDashboardGoals::overdueRatioAtMost), proposed.overdueRatioAtMost()),
                buildCountRow("Revisión de comprobantes, máximo", read(today, BillingDashboardGoals::receiptReviewBusinessDaysAtMost), proposed.receiptReviewBusinessDaysAtMost(),
                        "día hábil", "días hábiles"),
                buildPercentageRow("Avisos pagados en 72 horas, mínimo", read(today, BillingDashboardGoals::reminderPaidWithin72HoursAtLeast),
                        proposed.reminderPaidWithin72HoursAtLeast()));
    }

    static List<ChangeRow> buildCollectionReminderSettingsRows(CollectionReminderSettings today, CollectionReminderSettings proposed) {
        return List.of(
                buildFlagRow("Aviso al suspenderse", read(today, CollectionReminderSettings::suspensionNoticeEnabled), proposed.suspensionNoticeEnabled()),
                buildCountRow("Meses de resumen posterior a la baja", read(today, CollectionReminderSettings::postWithdrawalSummaryMonths), proposed.postWithdrawalSummaryMonths(),
                        "mes", "meses"),
                buildTimeRow("Envío de mails desde", read(today, CollectionReminderSettings::emailWindowStart), proposed.emailWindowStart()),
                buildTimeRow("Envío de mails hasta", read(today, CollectionReminderSettings::emailWindowEnd), proposed.emailWindowEnd()),
                buildCountRow("Tope diario de mails", read(today, CollectionReminderSettings::dailyEmailCap), proposed.dailyEmailCap(), "mail", "mails"),
                buildCountRow("Días de atraso para «Para contactar»", read(today, CollectionReminderSettings::contactListDaysOverdue), proposed.contactListDaysOverdue(), DAY, DAYS),
                buildCountRow("Cuotas vencidas para «Para contactar»", read(today, CollectionReminderSettings::contactListOverdueInstallments),
                        proposed.contactListOverdueInstallments(), "cuota", "cuotas"),
                buildFlagRow("Resumen diario activo", read(today, CollectionReminderSettings::dailyDigestEnabled), proposed.dailyDigestEnabled()));
    }

    private static <V, T> T read(V today, Function<V, T> getter) {
        return today == null ? null : getter.apply(today);
    }

    private static String issuanceModeText(IssuanceMode mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case REVIEW -> "Con revisión previa";
            case DIRECT -> "Emisión directa";
        };
    }

    private static List<String> hoursOf(List<Integer> hours) {
        return hours == null ? null : hours.stream().map(value -> value + " h").toList();
    }
}
