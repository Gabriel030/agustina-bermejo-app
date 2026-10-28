package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static com.red_escuela.backend.shared.domain.TestMoney.usd;
import static org.assertj.core.api.Assertions.assertThat;

class PostWithdrawalSummaryMessageComposerTest {
    private final PostWithdrawalSummaryMessageComposer composer = new PostWithdrawalSummaryMessageComposer();

    private static PostWithdrawalSummaryPlannedEvent eventWith(List<PostWithdrawalSummaryPlannedEvent.Block> blocks) {
        return PostWithdrawalSummaryPlannedEvent.plannedFor(1L, LocalDate.of(2027, 8, 2), blocks);
    }

    private static PostWithdrawalSummaryPlannedEvent.Block arsBlock() {
        return new PostWithdrawalSummaryPlannedEvent.Block(List.of(
                new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 10), ars(100000)),
                new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.ENROLLMENT_FEE, LocalDate.of(2027, 3, 5), ars(20000))), ars(120000));
    }

    @Test
    @Tag("D-338")
    void theMailListsEachDebtWithItsTotalWithoutAmountsInTheSubjectNorLateFeeOrSuspensionLines() {
        DeliveryContent mail = composer.composeEmail(eventWith(List.of(arsBlock())));

        assertThat(mail.subject()).isEqualTo("Resumen de tu deuda pendiente").doesNotContain("120");
        assertThat(mail.bodyText()).contains("- Cuota mensual: venció el 10/07/2027, ARS 100.000,00", "- Matrícula: venció el 05/03/2027, ARS 20.000,00",
                "Total adeudado en ARS: ARS 120.000,00", "subí el comprobante");
        assertThat(mail.bodyText()).doesNotContain("recargo", "suspend", "Deuda en ");
    }

    @Test
    @Tag("D-338")
    void thePostWithdrawalSummaryIsDispatchedAfterEveryOtherReminderWhenTheDailyCapIsShort() {
        int lowestPriorityOfTheOthers = java.util.Arrays.stream(com.red_escuela.backend.billing.events.PlannedReminderNoticeType.values())
                .mapToInt(com.red_escuela.backend.billing.events.PlannedReminderNoticeType::dispatchPriority).max().orElseThrow();

        assertThat(PostWithdrawalSummaryNotificationListener.DISPATCH_PRIORITY).isEqualTo(lowestPriorityOfTheOthers + 1).isEqualTo(7);
    }

    @Test
    @Tag("D-338")
    void severalCurrenciesAreSeparatedInTheMailAndInTheInAppMessage() {
        PostWithdrawalSummaryPlannedEvent.Block usdBlock = new PostWithdrawalSummaryPlannedEvent.Block(List.of(
                new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, LocalDate.of(2027, 7, 10), usd(50))), usd(50));
        PostWithdrawalSummaryPlannedEvent event = eventWith(List.of(arsBlock(), usdBlock));

        assertThat(composer.composeEmail(event).bodyText()).contains("Deuda en ARS:", "Deuda en USD:", "Total adeudado en USD: USD 50,00");
        assertThat(composer.composeInApp(event).bodyText()).contains("Total adeudado en ARS: ARS 120.000,00.", "Total adeudado en USD: USD 50,00.");
        assertThat(composer.composeInApp(event).subject()).isNull();
    }

    private static PostWithdrawalSummaryPlannedEvent.Block namedBlock() {
        return new PostWithdrawalSummaryPlannedEvent.Block(List.of(
                new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, LocalDate.of(2027, 3, 11), ars(100000), "Tecnicatura en Enfermería",
                        LocalDate.of(2027, 3, 15)),
                new PostWithdrawalSummaryPlannedEvent.Line(PlannedReminderInvoiceKind.ENROLLMENT_FEE, LocalDate.of(2027, 3, 1), ars(45000), "Curso de Cocina", null)), ars(145000));
    }

    @Test
    @Tag("D-338")
    void theMailNamesTheWithdrawnEnrollmentOfEachLineWithItsWithdrawalDate() {
        DeliveryContent mail = composer.composeEmail(eventWith(List.of(namedBlock())));

        assertThat(mail.bodyText()).contains("- Cuota mensual · Tecnicatura en Enfermería (baja del 15/03/2027): venció el 11/03/2027, ARS 100.000,00",
                "- Matrícula · Curso de Cocina: venció el 01/03/2027, ARS 45.000,00");
    }

    @Test
    @Tag("D-338")
    void theInAppMessageNamesTheWithdrawnEnrollmentToo() {
        DeliveryContent inApp = composer.composeInApp(eventWith(List.of(namedBlock())));

        assertThat(inApp.bodyText()).contains("Cuota mensual · Tecnicatura en Enfermería (baja del 15/03/2027) (venció el 11/03/2027, ARS 100.000,00)",
                "Matrícula · Curso de Cocina (venció el 01/03/2027, ARS 45.000,00)");
    }

    @Test
    @Tag("D-345")
    @Tag("D-341")
    void theNamedMailKeepsTheInformativeToneWithNoAmountsInTheSubjectAndTellsWhereToUploadTheReceipt() {
        DeliveryContent mail = composer.composeEmail(eventWith(List.of(namedBlock())));

        assertThat(mail.subject()).isEqualTo("Resumen de tu deuda pendiente").doesNotContainPattern("\\d");
        assertThat(mail.bodyText()).contains("Si ya realizaste el pago, subí el comprobante desde la sección Pagos de la plataforma.");
        assertThat(mail.bodyText().toLowerCase()).doesNotContain("intimación", "acciones legales", "último aviso");
    }
}
