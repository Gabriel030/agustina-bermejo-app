package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.block;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.ownNotice;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.receiptUnderReviewLine;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderMessageComposerTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);
    private static final LocalDate OVERDUE_DUE_DATE = LocalDate.of(2027, 3, 5);
    private static final LocalDate UPCOMING_DUE_DATE = LocalDate.of(2027, 3, 11);

    private final CollectionReminderMessageComposer composer = new CollectionReminderMessageComposer();

    private static PlannedReminderMessage ownNoticeOf(PlannedReminderNoticeType noticeType) {
        return ownNotice(noticeType, false, arsBlock(line(1, UPCOMING_DUE_DATE, ars(100000), false, noticeType)));
    }

    @ParameterizedTest
    @EnumSource(PlannedReminderNoticeType.class)
    @Tag("D-345")
    void subjectNeverContainsAmountsNorClaimLanguage(PlannedReminderNoticeType noticeType) {
        DeliveryContent email = composer.composeEmail(ownNoticeOf(noticeType), TODAY);

        assertThat(email.subject()).doesNotContainPattern("\\d").doesNotContain("$", "ARS", "USD");
        assertThat(email.subject().toLowerCase()).doesNotContain("intimación", "estudio jurídico", "acciones legales", "último aviso");
    }

    @ParameterizedTest
    @EnumSource(PlannedReminderNoticeType.class)
    @Tag("D-345")
    void bodiesNeverUseClaimLanguage(PlannedReminderNoticeType noticeType) {
        PlannedReminderMessage message = ownNoticeOf(noticeType);

        String emailBody = composer.composeEmail(message, TODAY).bodyText().toLowerCase();
        String inAppBody = composer.composeInApp(message, TODAY).bodyText().toLowerCase();

        assertThat(emailBody).doesNotContain("intimación", "estudio jurídico", "acciones legales", "último aviso");
        assertThat(inAppBody).doesNotContain("intimación", "estudio jurídico", "acciones legales", "último aviso");
    }

    @Test
    @Tag("D-409")
    @Tag("D-089")
    void theDigestListsEveryDebtLineInOrderAndMarksTheOnesWithTodaysNotice() {
        PlannedReminderLine overdueWithoutNotice = line(1, OVERDUE_DUE_DATE, ars(100000), false);
        PlannedReminderLine upcomingWithNotice = line(2, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(overdueWithoutNotice, upcomingWithNotice));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body.indexOf("venció el 05/03/2027")).isGreaterThanOrEqualTo(0).isLessThan(body.indexOf("vence el 11/03/2027"));
        assertThat(body).contains("ARS 100.000,00", "ARS 70.000,00", "Total adeudado en ARS: ARS 170.000,00");
        assertThat(body.lines().filter(text -> text.contains("(aviso de hoy)"))).singleElement().asString().contains("vence el 11/03/2027");
    }

    @Test
    @Tag("D-409")
    void anOwnNoticeDoesNotMarkItsOnlyLine() {
        String body = composer.composeEmail(ownNoticeOf(PlannedReminderNoticeType.BEFORE_DUE_REMINDER), TODAY).bodyText();

        assertThat(body).doesNotContain("aviso de hoy");
    }

    @Test
    @Tag("D-353")
    @Tag("D-097")
    void twoCurrenciesProduceTwoBlocksWithTheirOwnTotalsAndNeverASum() {
        Money dollars = Money.of(new BigDecimal("100.00"), Currency.USD);
        PlannedReminderBlock usdBlock = block(Currency.USD, dollars, Money.zero(Currency.USD), null, Money.zero(Currency.USD),
                line(2, UPCOMING_DUE_DATE, dollars, true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER));
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true,
                arsBlock(line(1, UPCOMING_DUE_DATE, ars(100000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER)), usdBlock);

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("Deuda en ARS:", "Deuda en USD:", "Total adeudado en ARS: ARS 100.000,00", "Total adeudado en USD: USD 100,00");
    }

    @Test
    @Tag("D-346")
    void aLineWithAReceiptUnderReviewSaysSo() {
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(receiptUnderReviewLine(1, OVERDUE_DUE_DATE, ars(100000)),
                line(2, OVERDUE_DUE_DATE, ars(50000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER)));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body.lines().filter(text -> text.contains("comprobante en revisión"))).singleElement().asString().contains("ARS 100.000,00");
    }

    @Test
    @Tag("D-139")
    void aCreditThatFallsShortNamesTheListedInvoiceItDoesNotCover() {
        PlannedReminderLine overdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false,
                block(Currency.ARS, ars(100000), ars(30000), 2L, ars(70000), overdue));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("Tenés ARS 30.000,00 a favor.", "Te faltan ARS 70.000,00 para cubrir la factura de cuota mensual (venció el 05/03/2027).");
    }

    @Test
    @Tag("D-139")
    @Tag("D-410")
    void theExemptMailWithAShortfallOnAnInvoiceOutsideItsLinesNamesTheNextInvoiceGenerically() {
        PlannedReminderLine exempt = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER);
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER, false,
                block(Currency.ARS, ars(100000), ars(30000), 1L, ars(40000), exempt));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("Te faltan ARS 40.000,00 para cubrir tu próxima factura.").doesNotContain("la factura de cuota mensual");
    }

    @Test
    @Tag("D-345")
    void theInAppSummaryNamesTheNoticedInvoicesAndTheTotalsOnly() {
        PlannedReminderLine overdueWithoutNotice = line(1, OVERDUE_DUE_DATE, ars(100000), false);
        PlannedReminderLine upcomingWithNotice = line(2, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(overdueWithoutNotice, upcomingWithNotice));

        DeliveryContent inApp = composer.composeInApp(message, TODAY);

        assertThat(inApp.subject()).isNull();
        assertThat(inApp.bodyText()).contains("Cuota mensual (vence el 11/03/2027, ARS 70.000,00)", "Total adeudado en ARS: ARS 170.000,00").doesNotContain("05/03/2027");
    }

    @Test
    @Tag("D-345")
    void aDebitNoteLineIsIndentedUnderItsInvoiceAndLabeledAsLateFee() {
        PlannedReminderLine invoice = line(1, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderLine debitNote = new PlannedReminderLine(3L, 1L, com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind.LATE_FEE, true,
                OVERDUE_DUE_DATE, ars(1700), false, false, java.util.List.of(), 1L, null, null, 2L, null);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(invoice, debitNote));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("\n    - Recargo por mora: venció el 05/03/2027, ARS 1.700,00");
    }

    private static PlannedReminderLine lineWithLateFee(long invoiceId, LocalDate dueDate, PlannedReminderNoticeType noticeType) {
        return lineWithLateFee(invoiceId, dueDate, noticeType, ars(100000));
    }

    private static PlannedReminderLine lineWithLateFee(long invoiceId, LocalDate dueDate, PlannedReminderNoticeType noticeType, Money balance) {
        PlannedReminderLine base = line(invoiceId, dueDate, balance, true, noticeType);
        return new PlannedReminderLine(base.invoiceId(), null, base.invoiceKind(), false, dueDate, base.balance(), false, base.precedesDueDate(), base.notices(),
                1L, null, null, 2L, new com.red_escuela.backend.billing.events.PlannedReminderLateFee(
                com.red_escuela.backend.shared.domain.Percentage.ofPoints(new BigDecimal("0.17")), 5, ars(100000)));
    }

    @Test
    @Tag("D-345")
    @Tag("D-330")
    @Tag("D-297")
    void theDailyAmountIsOverTheFinalPriceEvenWithAPartialPayment() {
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false,
                arsBlock(lineWithLateFee(1, LocalDate.of(2027, 3, 11), PlannedReminderNoticeType.AFTER_DUE_REMINDER, ars(40000))));

        String body = composer.composeEmail(message, LocalDate.of(2027, 3, 21)).bodyText();

        assertThat(body).contains("(ARS 170,00 por día)").contains("ARS 40.000,00");
    }

    private static PlannedReminderMessage withRule(PlannedReminderMessage message, int days, int invoices) {
        return new PlannedReminderMessage(message.format(), message.leadNoticeType(), message.priority(), message.precedesDueDate(), message.blocks(),
                new com.red_escuela.backend.billing.events.PlannedReminderSuspensionRule(days, invoices));
    }

    @Test
    @Tag("D-345")
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-288")
    void beforeDueMailStatesTheDailyLateFeeWithTheGraceAndTheAmountPerDay() {
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true,
                arsBlock(lineWithLateFee(1, LocalDate.of(2027, 3, 11), PlannedReminderNoticeType.BEFORE_DUE_REMINDER)));

        String body = composer.composeEmail(message, LocalDate.of(2027, 3, 10)).bodyText();

        assertThat(body).contains("si se paga después del 16/03/2027, corre un recargo de 0,17 % por día desde el 11/03/2027 (ARS 170,00 por día)");
    }

    @Test
    @Tag("D-345")
    @Tag("D-331")
    void afterTheGraceEndsTheLateFeeSentenceIsStatedAsRunning() {
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false,
                arsBlock(lineWithLateFee(1, LocalDate.of(2027, 3, 11), PlannedReminderNoticeType.AFTER_DUE_REMINDER)));

        String body = composer.composeEmail(message, LocalDate.of(2027, 3, 21)).bodyText();

        assertThat(body).contains("corre un recargo de 0,17 % por día desde el 11/03/2027").doesNotContain("si se paga después");
    }

    @Test
    @Tag("D-345")
    void aLineWithoutLateFeeTermsGetsNoLateFeeSentence() {
        String body = composer.composeEmail(ownNoticeOf(PlannedReminderNoticeType.BEFORE_DUE_REMINDER), TODAY).bodyText();

        assertThat(body).doesNotContain("recargo").doesNotContain("suspende");
    }

    @Test
    @Tag("D-345")
    @Tag("D-306")
    void theSuspensionLineDescribesTheCurrentThresholds() {
        PlannedReminderMessage message = withRule(ownNoticeOf(PlannedReminderNoticeType.AFTER_DUE_REMINDER), 10, 2);

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("la inscripción puede suspenderse si hay 2 facturas vencidas o más, o si pasan más de 10 días desde el vencimiento");
        assertThat(composer.composeEmail(withRule(message, 1, 1), TODAY).bodyText()).contains("si hay 1 factura vencida o más, o si pasan más de 1 día desde");
    }

    @Test
    @Tag("D-347")
    @Tag("D-345")
    void debtSummaryHasItsOwnSubjectListsTheDebtAndMentionsTheNewInstallment() {
        PlannedReminderMessage summary = new PlannedReminderMessage(com.red_escuela.backend.billing.events.PlannedReminderFormat.DEBT_SUMMARY,
                PlannedReminderNoticeType.ISSUANCE_NOTICE, 3, false, java.util.List.of(arsBlock(
                        line(1, OVERDUE_DUE_DATE, ars(80000), false),
                        line(2, UPCOMING_DUE_DATE, ars(100000), true, PlannedReminderNoticeType.ISSUANCE_NOTICE))), null);

        DeliveryContent email = composer.composeEmail(summary, TODAY);

        assertThat(email.subject()).isEqualTo("Resumen de tu deuda");
        assertThat(email.bodyText()).contains("Este es el resumen de tu deuda.", "(cuota nueva)", "Además, se emitió una cuota nueva de cuota mensual que vence el 11/03/2027",
                "Los pagos se aplican primero a lo más vencido.");
        assertThat(email.bodyText().indexOf("venció el 05/03/2027")).isLessThan(email.bodyText().indexOf("vence el 11/03/2027"));
        assertThat(composer.composeInApp(summary, TODAY).bodyText()).startsWith("Este es el resumen de tu deuda.").contains("ARS 80.000,00", "ARS 100.000,00");
    }

    @ParameterizedTest
    @EnumSource(com.red_escuela.backend.billing.events.PlannedReminderFormat.class)
    @Tag("D-345")
    void everyFormatSubjectIsFreeOfDigitsAmountsAndClaimLanguage(com.red_escuela.backend.billing.events.PlannedReminderFormat format) {
        for (PlannedReminderNoticeType type : PlannedReminderNoticeType.values()) {
            PlannedReminderMessage message = new PlannedReminderMessage(format, type, type.dispatchPriority(), false,
                    java.util.List.of(arsBlock(lineWithLateFee(1, UPCOMING_DUE_DATE, type))), null);

            String subject = composer.composeEmail(message, TODAY).subject();

            assertThat(subject).doesNotContainPattern("\\d").doesNotContain("$", "ARS", "USD");
            assertThat(subject.toLowerCase()).doesNotContain("intimación", "estudio jurídico", "acciones legales", "último aviso");
        }
    }

    @ParameterizedTest
    @EnumSource(com.red_escuela.backend.billing.events.PlannedReminderFormat.class)
    @Tag("D-345")
    void everyFormatBodyWithTheNewLinesAvoidsClaimLanguage(com.red_escuela.backend.billing.events.PlannedReminderFormat format) {
        PlannedReminderMessage message = withRule(new PlannedReminderMessage(format, PlannedReminderNoticeType.AFTER_DUE_REMINDER, 2, false,
                java.util.List.of(arsBlock(lineWithLateFee(1, OVERDUE_DUE_DATE, PlannedReminderNoticeType.AFTER_DUE_REMINDER))), null), 10, 2);

        String body = composer.composeEmail(message, TODAY).bodyText().toLowerCase();

        assertThat(body).contains("recargo", "suspenderse").doesNotContain("intimación", "estudio jurídico", "acciones legales", "último aviso");
    }
}
