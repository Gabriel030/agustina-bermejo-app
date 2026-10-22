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
                OVERDUE_DUE_DATE, ars(1700), false, false, java.util.List.of(), 1L, null, null, 2L);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(invoice, debitNote));

        String body = composer.composeEmail(message, TODAY).bodyText();

        assertThat(body).contains("\n    - Recargo por mora: venció el 05/03/2027, ARS 1.700,00");
    }
}
